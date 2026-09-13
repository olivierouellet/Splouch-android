package app.splouch.core.session

import app.splouch.core.board.ResultsBoard
import app.splouch.core.board.ScoreboardState
import app.splouch.core.clock.RaceClock
import app.splouch.core.schedule.HeatRef
import app.splouch.core.transport.SocketEvent
import app.splouch.core.transport.SplouchSocket
import app.splouch.core.transport.WebSocketTransport
import app.splouch.core.wire.Frame
import app.splouch.core.wire.MeetLive
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.ScoreboardFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One open meet: the three sockets of app.md C-01 and the tab models they feed.
 *
 * - `/ws/scoreboard` → [ScoreboardState], plus `meet_live` and `reload`
 * - `/ws/results` → [ResultsBoard], plus `meet_live` and `reload`
 * - `/ws/schedule` → [scheduleUpdates] (S-21)
 * - the current heat for the Schedule tab is read off the first two (S-05)
 *
 * `join_meet {meet_id, vid}` goes out on every connect of every socket, on a cloud only
 * (C-02), with the `vid` minted for this server (C-10).
 *
 * Runs on [scope], which must be single-threaded.
 */
class MeetSession(
    val context: MeetContext,
    transport: WebSocketTransport,
    private val vidStore: VidStore,
    private val scope: CoroutineScope,
    numLanes: Int,
    timing: SplouchSocket.Timing = SplouchSocket.Timing(),
    timeSource: kotlin.time.TimeSource.WithComparableMarks = kotlin.time.TimeSource.Monotonic,
    clock: RaceClock = RaceClock(timeSource),
) {
    val board = ScoreboardState(numLanes, clock)
    val results = ResultsBoard(numLanes)

    private val _scoreboard = MutableStateFlow(board.view())
    val scoreboard: StateFlow<ScoreboardState.View> = _scoreboard

    private val _resultsView = MutableStateFlow(results.view())
    val resultsView: StateFlow<ResultsBoard.View> = _resultsView

    private val _currentHeat = MutableStateFlow<HeatRef?>(null)
    val currentHeat: StateFlow<HeatRef?> = _currentHeat

    private val scheduleChannel = Channel<Unit>(Channel.CONFLATED)
    /** S-21: the start list changed; re-fetch it. */
    val scheduleUpdates: Flow<Unit> = scheduleChannel.receiveAsFlow()

    private val reloadChannel = Channel<Unit>(Channel.CONFLATED)
    /** C-08: settings or theme changed; re-fetch config and redraw. */
    val reloads: Flow<Unit> = reloadChannel.receiveAsFlow()

    private val reconnectChannel = Channel<Unit>(Channel.CONFLATED)
    /** A-09: a socket reconnected; the owner re-fetches config to learn whether the meet is gone. */
    val reconnects: Flow<Unit> = reconnectChannel.receiveAsFlow()

    val scoreboardSocket = SplouchSocket(context.wsUrl("/ws/scoreboard"), transport, scope, timing, timeSource)
    val resultsSocket = SplouchSocket(context.wsUrl("/ws/results"), transport, scope, timing, timeSource)
    val scheduleSocket = SplouchSocket(context.wsUrl("/ws/schedule"), transport, scope, timing, timeSource)
    private val sockets = listOf(scoreboardSocket, resultsSocket, scheduleSocket)

    private var scoreboardConnects = 0
    private var tickerJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { scoreboardSocket.events.collect(::onScoreboardEvent) }
        scope.launch { resultsSocket.events.collect(::onResultsEvent) }
        scope.launch { scheduleSocket.events.collect(::onScheduleEvent) }
        sockets.forEach { it.start() }
    }

    /** C-05: foreground or network restored — probe every socket. */
    fun wake() = sockets.forEach { it.wake() }

    /** The app came to the foreground: probe, and let the ticker run again. */
    fun foreground() {
        wake()
        startTicker()
    }

    /** The app went to the background (L-12): stop the ticker and forget the base. */
    fun background() {
        stopTicker()
        board.stopClock()
        publishScoreboard()
    }

    /** R-10: the Results tab was revealed — re-assert the room, reconnecting first if needed. */
    fun revealResults() {
        if (resultsSocket.isConnected) join(resultsSocket) else resultsSocket.wake()
    }

    /** L-14: the Scoreboard tab was revealed — refresh what the clock shows. */
    fun revealScoreboard() {
        board.tick()
        publishScoreboard()
    }

    fun close() {
        stopTicker()
        sockets.forEach { it.close() }
    }

    // ── ticker ────────────────────────────────────────────────────────────────

    fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(RaceClock.TICK)
                if (board.clock.isRunning) {
                    board.tick()
                    publishScoreboard()
                }
            }
        }
    }

    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ── socket handlers ───────────────────────────────────────────────────────

    private fun join(socket: SplouchSocket) {
        if (!context.joinsMeet) return
        socket.send(Frame.joinMeet(context.meetId!!, vidStore.vid(context.server.origin)))
    }

    private fun onScoreboardEvent(e: SocketEvent) {
        when (e) {
            SocketEvent.Connected -> {
                join(scoreboardSocket)
                board.onConnect()
                if (++scoreboardConnects > 1) reconnectChannel.trySend(Unit)
            }
            SocketEvent.Disconnected -> board.onDisconnect()
            is SocketEvent.Message -> when (e.event) {
                "update_scoreboard" -> ScoreboardFrame.fromJson(e.data)?.let { frame ->
                    board.apply(frame)
                    val ev = frame.currentEvent
                    val ht = frame.currentHeat
                    if (ev != null || ht != null) {
                        val cur = _currentHeat.value
                        _currentHeat.value = HeatRef(ev ?: cur?.event ?: "", ht ?: cur?.heat ?: "")
                    }
                }
                "meet_live" -> board.setMeetLive(MeetLive.fromJson(e.data).live)
                "reload" -> reloadChannel.trySend(Unit)
                else -> Unit // C-07
            }
        }
        publishScoreboard()
    }

    private fun onResultsEvent(e: SocketEvent) {
        when (e) {
            SocketEvent.Connected -> join(resultsSocket)
            SocketEvent.Disconnected -> results.clear()
            is SocketEvent.Message -> when (e.event) {
                "results_snapshot" -> ResultsSnapshot.fromJson(e.data)?.let { s ->
                    results.apply(s)
                    _currentHeat.value = HeatRef(s.event, s.heat)
                }
                "meet_live" -> if (!MeetLive.fromJson(e.data).live) results.clear()
                "reload" -> reloadChannel.trySend(Unit)
                else -> Unit
            }
        }
        _resultsView.value = results.view()
    }

    private fun onScheduleEvent(e: SocketEvent) {
        when (e) {
            SocketEvent.Connected -> join(scheduleSocket)
            SocketEvent.Disconnected -> Unit
            is SocketEvent.Message -> if (e.event == "schedule_update") scheduleChannel.trySend(Unit)
        }
    }

    private fun publishScoreboard() {
        _scoreboard.value = board.view()
    }
}
