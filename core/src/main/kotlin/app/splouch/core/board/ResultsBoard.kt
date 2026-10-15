package app.splouch.core.board

import app.splouch.core.wire.EventNameParts
import app.splouch.core.wire.ResultsSnapshot

/**
 * The Results tab's grid (app.md §4): the last `results_snapshot`, held still, mapped
 * onto `numLanes` rows by lane or by place.
 */
class ResultsBoard(numLanes: Int, firstLane: Int = 1) {

    /** L-04's row count here too; [resize] follows a re-fetched config (C-08). */
    var numLanes: Int = numLanes
        private set

    /** L-04: the lane number of row 1. A result's `channel` is a lane number, never a row. */
    var firstLane: Int = firstLane
        private set

    fun resize(lanes: Int, firstLane: Int = this.firstLane) {
        numLanes = lanes
        this.firstLane = firstLane
    }

    data class Row(
        /**
         * The lane number, or empty for an unfilled rank in place sort — that row is no lane at
         * all, and its still water (L-25) says so without a dash.
         */
        val laneLabel: String,
        val name: String = "",
        val alt: String = "",
        val club: String = "",
        /** `—` when there is no final time (R-07). */
        val time: String = DASH,
        val deltaSeconds: Double? = null,
        val deltaBetter: Boolean? = null,
        /** Empty when none: no dash, no `#` (R-07). */
        val place: String = "",
        /** R-09: a final time carries the locked styling. */
        val locked: Boolean = false,
        /** L-25: no result on this row — drawn as still water rather than a dash. */
        val vacant: Boolean = false,
    )

    data class View(
        val event: String,
        val heat: String,
        val eventName: String,
        val eventNameParts: EventNameParts?,
        /** R-01: no snapshot yet, or wiped (R-02). */
        val waiting: Boolean,
        val rows: List<Row>,
    )

    private var snapshot: ResultsSnapshot? = null

    fun view(): View {
        val s = snapshot ?: return View("", "", "", null, waiting = true, rows = blankRows(laneSort = true))
        return View(s.event, s.heat, s.eventName, s.eventNameParts, waiting = false, rows = rowsFor(s))
    }

    /** A snapshot with no lanes is ignored, as the web does: nothing to show, nothing to wipe. */
    fun apply(s: ResultsSnapshot) {
        if (s.lanes.isEmpty()) return
        snapshot = s
    }

    /** R-02: a disconnect or `meet_live` false returns the board to the waiting state. */
    fun clear() {
        snapshot = null
    }

    private fun rowsFor(s: ResultsSnapshot): List<Row> {
        val laneSort = s.isLaneSort
        val byRow = HashMap<Int, app.splouch.core.wire.ResultLane>()
        s.lanes.forEachIndexed { idx, r ->
            // api.md §5.2: `channel` is the lane number — 0 is a lane, only null is none.
            val row = if (laneSort) r.channel?.let { it - firstLane + 1 } ?: -1 else idx + 1
            if (row in 1..numLanes) byRow[row] = r
        }
        return (1..numLanes).map { i ->
            val r = byRow[i]
            if (r == null) {
                // R-06: an unfilled rank is no lane at all.
                Row(laneLabel = if (laneSort) label(i) else "", vacant = true)
            } else {
                Row(
                    laneLabel = r.channel?.toString() ?: label(i),
                    name = r.name,
                    alt = r.alt,
                    club = r.club,
                    time = r.time.ifEmpty { DASH },
                    deltaSeconds = r.deltaSeconds,
                    deltaBetter = r.deltaBetter,
                    place = r.place,
                    locked = r.time.isNotEmpty(),
                )
            }
        }
    }

    private fun blankRows(laneSort: Boolean) = (1..numLanes).map {
        Row(laneLabel = if (laneSort) label(it) else DASH)
    }

    /** L-04: row [i]'s lane number. */
    private fun label(i: Int) = (i + firstLane - 1).toString()

    companion object {
        const val DASH = "—"
    }
}
