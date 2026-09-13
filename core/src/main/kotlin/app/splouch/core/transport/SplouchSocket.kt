package app.splouch.core.transport

import app.splouch.core.wire.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/** What a socket reports to its owner. `pong` never appears: it is liveness only. */
sealed interface SocketEvent {
    /** The socket opened — on every connect and reconnect. A cloud owner sends `join_meet` here. */
    data object Connected : SocketEvent

    /** An open socket died. Implies `meet_live = false` (app.md C-09). */
    data object Disconnected : SocketEvent

    data class Message(val event: String, val data: JsonElement?) : SocketEvent
}

/**
 * One reconnecting Splouch socket — the loop of app.md §6, mirroring `ws.js`:
 *
 * - connect; on open flush the queue and report [SocketEvent.Connected] (C-06)
 * - `ping` every [Timing.heartbeat]; no inbound frame for [Timing.stale] means dead (C-04)
 * - [wake] on foreground or network-restored: `ping`, and no frame within
 *   [Timing.probeTimeout] means dead (C-05)
 * - dead → tear down → reconnect after a capped exponential backoff (C-03)
 * - unknown events are passed through as [SocketEvent.Message]; owners ignore them (C-07)
 *
 * `join_meet` is deliberately not here: whether to send it depends on the server's
 * `kind` (C-02), which the owner knows.
 *
 * All state lives on [scope], which must be single-threaded (the main dispatcher in the
 * app, a test dispatcher in tests). Public methods are called from that thread too.
 * [events] is a single-consumer stream.
 */
class SplouchSocket(
    val url: String,
    private val transport: WebSocketTransport,
    private val scope: CoroutineScope,
    private val timing: Timing = Timing(),
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    data class Timing(
        val heartbeat: Duration = 15.seconds,
        val stale: Duration = 35.seconds,
        val probeTimeout: Duration = 4.seconds,
        val minBackoff: Duration = 500.milliseconds,
        val maxBackoff: Duration = 5.seconds,
    )

    private enum class Phase { IDLE, CONNECTING, OPEN, DEAD }

    private val channel = Channel<SocketEvent>(Channel.UNLIMITED)
    val events: Flow<SocketEvent> = channel.receiveAsFlow()

    private var phase = Phase.IDLE
    private var closed = false
    private var handle: WebSocketTransport.Handle? = null
    private var generation = 0
    private var backoff = timing.minBackoff
    private val queue = ArrayDeque<String>()

    /** Counts inbound frames; the stale and probe checks compare counts, not clocks. */
    private var received = 0L
    private var lastReceivedAt: ComparableTimeMark = timeSource.markNow()
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var probeJob: Job? = null

    val isConnected: Boolean get() = phase == Phase.OPEN

    /** Opens the first connection. Idempotent. */
    fun start() {
        if (closed || phase != Phase.IDLE) return
        connect()
    }

    /** Sends now when open, otherwise queues until the next open (C-06). */
    fun send(frame: Frame) {
        if (closed) return
        val text = frame.encode()
        if (phase == Phase.OPEN && handle?.send(text) == true) return
        queue.addLast(text)
    }

    fun send(event: String, data: JsonElement? = null) = send(Frame(event, data))

    /**
     * C-05: the app came to the foreground or the network came back. An open socket is
     * probed with a `ping` and dropped if nothing arrives within the probe timeout; a dead
     * one reconnects at once with the backoff reset.
     */
    fun wake() {
        if (closed) return
        when (phase) {
            Phase.OPEN -> {
                val mark = received
                sendRaw(Frame.ping().encode())
                probeJob?.cancel()
                probeJob = scope.launch {
                    delay(timing.probeTimeout)
                    if (phase == Phase.OPEN && received == mark) dropDead()
                }
            }
            Phase.DEAD -> {
                backoff = timing.minBackoff
                reconnectJob?.cancel()
                reconnectJob = null
                connect()
            }
            Phase.IDLE, Phase.CONNECTING -> Unit
        }
    }

    /** Permanent: no reconnect, and [events] completes. */
    fun close() {
        if (closed) return
        closed = true
        generation++
        stopTimers()
        reconnectJob?.cancel()
        reconnectJob = null
        handle?.close()
        handle = null
        phase = Phase.DEAD
        channel.close()
    }

    // ── connection lifecycle ──────────────────────────────────────────────────

    private fun connect() {
        reconnectJob?.cancel()
        reconnectJob = null
        phase = Phase.CONNECTING
        val gen = ++generation
        val listener = object : WebSocketTransport.Listener {
            override fun onOpen() = post(gen) { handleOpen() }
            override fun onMessage(text: String) = post(gen) { handleMessage(text) }
            override fun onClosed() = post(gen) { handleDead() }
            override fun onFailure(error: Throwable) = post(gen) { handleDead() }
        }
        handle = transport.connect(url, listener)
    }

    /** Marshals a transport callback onto the scope, dropping it if it belongs to an old attempt. */
    private fun post(gen: Int, block: () -> Unit) {
        scope.launch { if (gen == generation && !closed) block() }
    }

    private fun handleOpen() {
        if (phase != Phase.CONNECTING) return
        phase = Phase.OPEN
        backoff = timing.minBackoff
        markReceived()
        while (queue.isNotEmpty()) {
            val text = queue.removeFirst()
            if (handle?.send(text) != true) {
                queue.addFirst(text)
                break
            }
        }
        startHeartbeat()
        emit(SocketEvent.Connected)
    }

    private fun handleMessage(text: String) {
        markReceived()
        val frame = Frame.decode(text) ?: return
        if (frame.event == "pong") return
        emit(SocketEvent.Message(frame.event, frame.data))
    }

    private fun handleDead() {
        if (phase == Phase.DEAD || phase == Phase.IDLE) return
        val wasOpen = phase == Phase.OPEN
        phase = Phase.DEAD
        handle = null
        stopTimers()
        if (wasOpen) emit(SocketEvent.Disconnected)
        scheduleReconnect()
    }

    /** The socket looks open but is not answering: tear it down without waiting on it. */
    private fun dropDead() {
        generation++
        handle?.abort()
        handleDead()
    }

    private fun scheduleReconnect() {
        if (closed || reconnectJob != null) return
        val wait = backoff
        backoff = minOf(backoff * 2, timing.maxBackoff)
        reconnectJob = scope.launch {
            delay(wait)
            reconnectJob = null
            if (!closed) connect()
        }
    }

    // ── heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        stopTimers()
        heartbeatJob = scope.launch {
            while (isActive && phase == Phase.OPEN) {
                delay(timing.heartbeat)
                if (phase != Phase.OPEN) break
                if (lastReceivedAt.elapsedNow() > timing.stale) {
                    dropDead()
                    break
                }
                sendRaw(Frame.ping().encode())
            }
        }
    }

    private fun stopTimers() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        probeJob?.cancel()
        probeJob = null
    }

    private fun markReceived() {
        received++
        lastReceivedAt = timeSource.markNow()
    }

    private fun sendRaw(text: String) {
        handle?.send(text)
    }

    private fun emit(event: SocketEvent) {
        channel.trySend(event)
    }
}
