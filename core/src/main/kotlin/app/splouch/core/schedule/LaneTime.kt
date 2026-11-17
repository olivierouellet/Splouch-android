package app.splouch.core.schedule

import app.splouch.core.board.DeltaFormat
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScheduleLane

/**
 * What a Schedule lane's time cell shows (app.md S-22, S-23): the best time known —
 * official result or its status, else the console's, else the seed — or, while a heat is
 * swapped (S-23), the gap to the seed: the official result's once there is one, else the
 * console time's.
 *
 * [spokenKey] is the `[mobile]` word a screen reader says for the cell, before [text] when
 * [speaksText] — "Official time 30.12", but "Disqualified" alone.
 */
data class LaneTime(val text: String, val kind: Kind, val spokenKey: String, val speaksText: Boolean = true) {
    enum class Kind { SEED, CONSOLE, OFFICIAL, BETTER, WORSE }

    companion object {
        /** Every time travels as `HH:MM:SS.hh`; the hours go when there are none. */
        fun display(time: String): String = if (time.startsWith("00:") && time.length > 8) time.substring(3) else time

        private val CONSOLE = Regex("""^\s*(?:(\d+):)?(\d{1,2})\.(\d{2})\s*$""")

        /** A console time as `results_snapshot` carries it (`58.21`, `1:02.34`), in wire form. */
        fun wire(time: String): String {
            val m = CONSOLE.matchEntire(time) ?: return ""
            val (min, sec, cent) = m.destructured
            val h = ((min.ifEmpty { "0" }.toInt() * 60 + sec.toInt()) * 100) + cent.toInt()
            if (h <= 0) return ""
            fun two(n: Int) = n.toString().padStart(2, '0')
            return "${two(h / 360000)}:${two(h / 6000 % 60)}:${two(h / 100 % 60)}.${two(h % 100)}"
        }

        /**
         * S-23: a heat whose times swap on a tap for their gaps to the seed — an official
         * one, or one the console has timed.
         */
        fun swaps(official: Boolean, lanes: List<ScheduleLane>): Boolean =
            official || lanes.any { it.consoleTime.isNotEmpty() }

        private val LONG = Regex("""^\s*(\d+):(\d{2}):(\d{2})\.(\d{2})\s*$""")

        /**
         * A time in hundredths: `HH:MM:SS.hh` as the schedule carries it, or a seed as Hytek
         * wrote it (`58.21`, `1:02.34`) — the server's own `parse_time_hundredths`. Null when
         * empty, zero or not a time.
         */
        fun hundredths(time: String): Int? {
            val total = LONG.matchEntire(time)?.destructured?.let { (h, m, s, c) ->
                ((h.toInt() * 60 + m.toInt()) * 60 + s.toInt()) * 100 + c.toInt()
            } ?: CONSOLE.matchEntire(time)?.destructured?.let { (m, s, c) ->
                (m.ifEmpty { "0" }.toInt() * 60 + s.toInt()) * 100 + c.toInt()
            }
            return total?.takeIf { it > 0 }
        }

        /**
         * `console_time` − `seed_time` in hundredths, on the server's rule. Null with no
         * console time, no seed, or a seed from another course (S-27).
         */
        private fun consoleGap(lane: ScheduleLane): Int? = if (lane.seedCourse.isEmpty()) {
            hundredths(lane.consoleTime)?.let { c -> hundredths(lane.seedTime)?.let { c - it } }
        } else {
            null
        }

        private fun gap(d: Int) =
            LaneTime(DeltaFormat.text(d / 100.0), if (d < 0) Kind.BETTER else Kind.WORSE, "seed_diff")

        /**
         * The cell for [lane]; with [diff], its S-23 gap to the seed. [late] is the swap's
         * second half, where a status lane, which has no official gap, turns from its console
         * time to the console's gap.
         */
        fun of(lane: ScheduleLane, diff: Boolean = false, late: Boolean = false): LaneTime? {
            val status = lane.resultStatus
            if (diff) {
                when {
                    status.isNotEmpty() -> {
                        if (late) consoleGap(lane)?.let { return gap(it) }
                        if (lane.consoleTime.isNotEmpty()) {
                            return LaneTime(display(lane.consoleTime), Kind.CONSOLE, "time_console")
                        }
                    }
                    lane.resultDeltaSeconds != null -> return LaneTime(
                        DeltaFormat.text(lane.resultDeltaSeconds),
                        if (lane.resultDeltaBetter == true) Kind.BETTER else Kind.WORSE,
                        "seed_diff",
                    )
                    else -> {
                        // Not official yet: the console's own gap.
                        if (lane.resultTime.isEmpty()) consoleGap(lane)?.let { return gap(it) }
                        return LaneTime(seedText(lane), Kind.SEED, "time_seed")
                    }
                }
            }
            return when {
                status.isNotEmpty() -> LaneTime(
                    status,
                    Kind.OFFICIAL,
                    "status_${status.lowercase()}",
                    speaksText = false,
                )
                lane.resultTime.isNotEmpty() -> LaneTime(display(lane.resultTime), Kind.OFFICIAL, "time_official")
                lane.consoleTime.isNotEmpty() -> LaneTime(display(lane.consoleTime), Kind.CONSOLE, "time_console")
                lane.seedTime.isNotEmpty() -> LaneTime(seedText(lane), Kind.SEED, "time_seed")
                else -> null
            }
        }

        /**
         * The seed as drawn: `NT` for none, and the course after it when it is not the meet's
         * (S-27), `01:01.00 LCM`. A course is the same three letters in every language.
         */
        fun seedText(lane: ScheduleLane): String = when {
            lane.seedTime.isEmpty() -> "NT"
            lane.seedCourse.isEmpty() -> display(lane.seedTime)
            else -> display(lane.seedTime) + " " + lane.seedCourse
        }

        /**
         * A finished heat's console times, from the `results_snapshot` that announced it
         * (S-22): patched in rather than re-fetching every heat. A lane's `channel` is its
         * lane number; the heat is matched on its round too (S-24).
         */
        fun applyConsoleTimes(heats: List<ScheduleHeat>, snap: ResultsSnapshot): List<ScheduleHeat> {
            val times = snap.lanes.mapNotNull { r ->
                val t = wire(r.time)
                if (r.channel == null || t.isEmpty()) null else r.channel to t
            }.toMap()
            if (times.isEmpty()) return heats
            val ref = HeatRef(snap.event, snap.heat, snap.round)
            return heats.map { h ->
                if (!ref.matches(h)) {
                    h
                } else {
                    h.copy(lanes = h.lanes.map { l -> times[l.lane]?.let { l.copy(consoleTime = it) } ?: l })
                }
            }
        }
    }
}
