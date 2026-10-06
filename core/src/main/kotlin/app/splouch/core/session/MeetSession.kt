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
import app.splouch.core.wire.Moved
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
 * (C-02), with the `vid` minted for this server (C-10) — the server the meet list came
 * from, even when the meet's `base` is on another host, so one phone is one visitor. A
 * spectator who refused counting joins without one; the setting is read on every join.
 *
 * The sockets open at the meet's `base` (C-11). A `moved {url, base}` on any of them
 * (C-12) switches all three to the new base at once and reports it on [moves], so the
 * owner can re-fetch config there.
 *
 * Runs on [scope], which must be single-threaded.
 */
class MeetSession(
    context: MeetContext,
    private val transport: WebSocketTransport,
    private val vidStore: VidStore,
    private val scope: CoroutineScope,
    numLanes: Int,
    private val timing: SplouchSocket.Timing = SplouchSocket.Timing(),
    private val timeSource: kotlin.time.TimeSource.WithComparableMarks = kotlin.time.TimeSource.Monotonic,
    clock: RaceClock = RaceClock(timeSource),
) {
    /** Where the session is now: its `base` changes on a move (C-12), its server never. */
    var context: MeetContext = context
        private set

    val board = ScoreboardState(numLanes, clock)
    val results = ResultsBoard(numLanes)

    private val _scoreboard = MutableStateFlow(board.view())
    val scoreboard: StateFlow<ScoreboardState.View> = _scoreboard

    private val _resultsView = MutableStateFlow(results.view())
    val resultsView: StateFlow<ResultsBoard.View> = _resultsView

    private val _currentHeat = MutableStateFlow<HeatRef?>(null)
    val currentHeat: StateFlow<HeatRef?> = _currentHeat

    private val scheduleChannel = Channel<Unit>(Channel.CONFLATED)
    private val resultsChannel = Channel<ResultsSnapshot>(Channel.UNLIMITED)

    /** Every `results_snapshot`: its heat's console times belong on the Schedule too (S-22). */
    val resultsFrames: Flow<ResultsSnapshot> = resultsChannel.receiveAsFlow()

    /** S-21: the start list changed; re-fetch it. */
    val scheduleUpdates: Flow<Unit> = scheduleChannel.receiveAsFlow()

    private val reloadChannel = Channel<Unit>(Channel.CONFLATED)

    /** C-08: settings or theme changed; re-fetch config and redraw. */
    val reloads: Flow<Unit> = reloadChannel.receiveAsFlow()

    private val reconnectChannel = Channel<Unit>(Channel.CONFLATED)

    /** A-09: a socket reconnected; the owner re-fetches config to learn whether the meet is gone. */
    val reconnects: Flow<Unit> = reconnectChannel.receiveAsFlow()

    private val moveChannel = Channel<MeetBase>(Channel.CONFLATED)

    /** C-12: the sockets followed the meet to this base; the owner re-fetches config there. */
    val moves: Flow<MeetBase> = moveChannel.receiveAsFlow()

    var scoreboardSocket = socket("/ws/scoreboard")
        private set
    var resultsSocket = socket("/ws/results")
        private set
    var scheduleSocket = socket("/ws/schedule")
        private set
    private val sockets get() = listOf(scoreboardSocket, resultsSocket, scheduleSocket)

    private var scoreboardConnects = 0
    private var tickerJob: Job? = null
    private var collectors: List<Job> = emptyList()
    private var started = false
    private var closed = false

    private fun socket(path: String) = SplouchSocket(context.wsUrl(path), transport, scope, timing, timeSource)

    fun start() {
        if (started) return
        started = true
        open()
    }

    /** Collects the current three and starts them; a collector outlives no socket it was not started for. */
    private fun open() {
        val sb = scoreboardSocket
        val rs = resultsSocket
        val sc = scheduleSocket
        collectors = listOf(
            scope.launch { sb.events.collect { if (sb === scoreboardSocket) onScoreboardEvent(it) } },
            scope.launch { rs.events.collect { if (rs === resultsSocket) onResultsEvent(it) } },
            scope.launch { sc.events.collect { if (sc === scheduleSocket) onScheduleEvent(it) } },
        )
        sockets.forEach { it.start() }
    }

    /**
     * C-12, and A-09's config naming a new `base`: the meet now lives at [base]. All three
     * sockets close and reopen there, joining as on any connect. The board is held as on a
     * drop (C-09) until the new worker replays it — a base that never answers must not leave
     * a clock ticking as if live. Same base, or a closed session: nothing to do.
     */
    fun moveTo(base: MeetBase) {
        if (closed || base == context.base) return
        context = context.copy(base = base)
        collectors.forEach { it.cancel() }
        collectors = emptyList()
        sockets.forEach { it.close() }
        scoreboardSocket = socket("/ws/scoreboard")
        resultsSocket = socket("/ws/results")
        scheduleSocket = socket("/ws/schedule")
        // The first connect at the new base is not a reconnect: the owner re-fetches config
        // on [moves] already.
        scoreboardConnects = 0
        board.onDisconnect()
        results.clear()
        publishScoreboard()
        _resultsView.value = results.view()
        if (started) open()
        moveChannel.trySend(base)
    }

    /** A `moved` frame, from whichever socket heard it first. One without a usable base is ignored (C-07). */
    private fun onMoved(data: kotlinx.serialization.json.JsonElement?) {
        val base = MeetBase.parse(Moved.fromJson(data)?.base) ?: return
        moveTo(base)
    }

    /** C-08: a re-fetched config changed `num_lanes` — both boards take the new row count. */
    fun resize(numLanes: Int) {
        if (numLanes == board.numLanes && numLanes == results.numLanes) return
        board.resize(numLanes)
        results.resize(numLanes)
        publishScoreboard()
        _resultsView.value = results.view()
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
        closed = true
        stopTicker()
        collectors.forEach { it.cancel() }
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
                // api.md §2.2, a local Pi only: the explicit wipe when a test session ends and
                // the operator's own meet has been reloaded. Distinct from `test_mode
                // {active: false}`, which only takes the badge down — an operator who stops a
                // replay to keep studying the last heat still has the board.
                "reset" -> {
                    board.reset()
                    _currentHeat.value = null
                }
                "meet_live" -> board.setMeetLive(MeetLive.fromJson(e.data).live)
                "reload" -> reloadChannel.trySend(Unit)
                "moved" -> return onMoved(e.data)
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
                    resultsChannel.trySend(s)
                }
                "meet_live" -> if (!MeetLive.fromJson(e.data).live) results.clear()
                "reload" -> reloadChannel.trySend(Unit)
                "moved" -> return onMoved(e.data)
                else -> Unit
            }
        }
        _resultsView.value = results.view()
    }

    private fun onScheduleEvent(e: SocketEvent) {
        when (e) {
            SocketEvent.Connected -> join(scheduleSocket)
            SocketEvent.Disconnected -> Unit
            is SocketEvent.Message -> when (e.event) {
                "schedule_update" -> scheduleChannel.trySend(Unit)
                "moved" -> onMoved(e.data)
                else -> Unit
            }
        }
    }

    private fun publishScoreboard() {
        _scoreboard.value = board.view()
    }
}
