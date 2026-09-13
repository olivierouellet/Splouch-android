package app.splouch.core.board

import app.splouch.core.clock.RaceClock
import app.splouch.core.wire.EventNameParts
import app.splouch.core.wire.ScoreboardFrame

/**
 * The Scoreboard tab's model: partial frames merged into lane state (app.md L-10), the
 * running/locked edges (L-11), the race clock's ownership of running lanes (L-12), and
 * what an event or heat change does to the figures (L-13).
 *
 * This is what the reference `scoreboard_base.html` does, minus its two artefacts: the
 * board starts with no "last event" so the join replay is a baseline rather than a
 * change, and a change frame always blanks unless a lane was running before it.
 *
 * Not thread-safe: drive it from one thread and read [view] there.
 */
class ScoreboardState(val numLanes: Int, val clock: RaceClock) {

    enum class TimeStyle { NORMAL, RUNNING, LOCKED }

    data class Lane(
        val number: Int,
        val name: String = "",
        val alt: String = "",
        val club: String = "",
        /** The time cell's text: a split or final, or the race clock while the lane runs. */
        val time: String = "",
        val deltaSeconds: Double? = null,
        val deltaBetter: Boolean? = null,
        /** Trimmed; empty when there is none, so no `#` is drawn (L-15). */
        val place: String = "",
        val running: Boolean = false,
        val timeStyle: TimeStyle = TimeStyle.NORMAL,
        /** Increments on every running→stopped edge: the key for the one-shot lock transition. */
        val lockEdge: Int = 0,
        /** The lane number pulses: running, live, and no clock to show (L-12 table). */
        val pulsing: Boolean = false,
    )

    data class View(
        val currentEvent: String,
        val currentHeat: String,
        val eventName: String,
        val eventNameParts: EventNameParts?,
        val meetLive: Boolean,
        val clockRunning: Boolean,
        val lanes: List<Lane>,
        /** Bumps when a frame carried a `lane_name<i>`: the L-17 re-fit gate. */
        val namesVersion: Int,
    )

    private val lanes = MutableList(numLanes) { Lane(it + 1) }
    private var currentEvent = ""
    private var currentHeat = ""
    private var eventName = ""
    private var eventNameParts: EventNameParts? = null
    private var meetLive = false
    private var lastEvent: String? = null
    private var lastHeat: String? = null
    private var namesVersion = 0

    fun view(): View = View(
        currentEvent, currentHeat, eventName, eventNameParts, meetLive, clock.isRunning, lanes.toList(), namesVersion,
    )

    /**
     * The socket (re)connected. The next event and heat are a baseline, not a change;
     * running flags are unknown again; the clock has no base. Cells keep their text until
     * the replay overwrites it, as the web does.
     */
    fun onConnect() {
        lastEvent = null
        lastHeat = null
        for (i in lanes.indices) lanes[i] = lanes[i].copy(running = false)
        clock.stop()
        refreshPulses()
    }

    /** C-09: a drop implies not live, which stops every clock on the board. */
    fun onDisconnect() = setMeetLive(false)

    fun setMeetLive(live: Boolean) {
        meetLive = live
        if (!live) clock.stop()
        refreshPulses()
    }

    /** Tab hidden or app backgrounded (L-12): the ticker stops and the base is forgotten. */
    fun stopClock() {
        clock.stop()
        refreshPulses()
    }

    /**
     * The 10Hz tick, and the paint that follows a re-base. Writes the clock into every
     * running lane; on the first frozen reading it writes that and stops, so the digits
     * hold where they stopped and the pulse takes over.
     */
    fun tick() {
        val reading = clock.reading() ?: return
        for (i in lanes.indices) if (lanes[i].running) lanes[i] = lanes[i].copy(time = reading.text)
        if (reading is RaceClock.Reading.Frozen) clock.stop()
        refreshPulses()
    }

    fun apply(frame: ScoreboardFrame) {
        val anyRunningBefore = lanes.any { it.running }

        // Running flags first: they decide whether the cells below take their time from
        // `lane_time<i>` or from the clock, and at a wall the flag and the split arrive in
        // the same frame.
        for ((i, running) in frame.runningEdges()) {
            if (i !in 1..numLanes) continue
            val lane = lanes[i - 1]
            lanes[i - 1] = when {
                running -> lane.copy(running = true, timeStyle = TimeStyle.RUNNING)
                lane.running -> lane.copy(running = false, timeStyle = TimeStyle.LOCKED, lockEdge = lane.lockEdge + 1)
                else -> lane.copy(running = false)
            }
        }

        // Then the clock, so the re-base paints before anything below can stamp a stale
        // split over it. A value that does not parse is no re-base at all.
        frame.runningTime?.let { if (clock.rebase(it)) tick() }

        frame.currentEvent?.let { currentEvent = it }
        frame.currentHeat?.let { currentHeat = it }
        frame.eventName?.let { eventName = it }
        if (frame.hasEventNameParts) eventNameParts = frame.eventNameParts

        if (frame.carriesNames()) namesVersion++
        for (i in 1..numLanes) {
            var lane = lanes[i - 1]
            frame.laneName(i)?.let { lane = lane.copy(name = it) }
            frame.laneNameAlt(i)?.let { lane = lane.copy(alt = it) }
            frame.laneClub(i)?.let { lane = lane.copy(club = it) }
            // A running lane's time cell belongs to the ticker: the split stays in the
            // server's snapshot and would otherwise sit on top of the live clock.
            frame.laneTime(i)?.let { if (!clockOwns(lane)) lane = lane.copy(time = it) }
            frame.lanePlace(i)?.let { lane = lane.copy(place = it.trim()) }
            if (frame.hasLaneDeltaSeconds(i)) lane = lane.copy(deltaSeconds = frame.laneDeltaSeconds(i))
            if (frame.hasLaneDeltaBetter(i)) lane = lane.copy(deltaBetter = frame.laneDeltaBetter(i))
            lanes[i - 1] = lane
        }

        // L-13. The first event and heat this connection sees are a baseline.
        var changed = false
        frame.currentEvent?.let { ev ->
            if (lastEvent == null) lastEvent = ev
            else if (ev != lastEvent) { lastEvent = ev; changed = true }
        }
        frame.currentHeat?.let { ht ->
            if (lastHeat == null) lastHeat = ht
            else if (ht != lastHeat) { lastHeat = ht; changed = true }
        }
        val anyRunningNow = lanes.any { it.running }
        // A lane running on this frame outranks everything; a lane running on the previous
        // frame means the console advanced before publishing results — keep them.
        if (changed && !anyRunningNow && !anyRunningBefore) intro()

        // Nothing running means nothing shows the clock; whoever stops it decides what
        // replaces the digits, and here the cells simply keep what they have.
        if (!anyRunningNow) clock.stop()
        refreshPulses()
    }

    private fun intro() {
        for (i in lanes.indices) {
            lanes[i] = lanes[i].copy(
                time = "", deltaSeconds = null, deltaBetter = null, place = "", timeStyle = TimeStyle.NORMAL,
            )
        }
    }

    private fun clockOwns(lane: Lane): Boolean = clock.isRunning && lane.running

    private fun refreshPulses() {
        val pulse = meetLive && !clock.isRunning
        for (i in lanes.indices) {
            val want = pulse && lanes[i].running
            if (lanes[i].pulsing != want) lanes[i] = lanes[i].copy(pulsing = want)
        }
    }
}
