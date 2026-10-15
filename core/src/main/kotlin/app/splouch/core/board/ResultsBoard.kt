package app.splouch.core.board

import app.splouch.core.session.ResultsOrder
import app.splouch.core.wire.EventNameParts
import app.splouch.core.wire.ResultLane
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
        /**
         * Which row this is wherever it is drawn: the lane's row in lane order, the rank in place
         * order. L-26 turns the lanes around by moving rows, and this is what moves.
         */
        val slot: Int = 0,
    )

    /**
     * What the tab draws. The rows are arranged on demand, in the reader's order (P-23) and
     * direction (L-26), which the tab holds and this does not.
     */
    data class View(
        val event: String,
        val heat: String,
        val eventName: String,
        val eventNameParts: EventNameParts?,
        /** R-01: no snapshot yet, or wiped (R-02). */
        val waiting: Boolean,
        /** The pool's order, `"lane"`, `"place"`, or null — read as lane (R-05). */
        val sort: String? = null,
        val lanes: List<ResultLane> = emptyList(),
        val numLanes: Int = 0,
        val firstLane: Int = 1,
    ) {
        /** The rows in the pool's order, lanes ascending. */
        val rows: List<Row> get() = rows(ResultsOrder.POOL, descending = false)

        /** R-05, R-06: in lane order unless the reader's, else the pool's, order is place. */
        fun isLaneOrder(order: ResultsOrder): Boolean = order.isLaneOrder(sort)

        /**
         * Lane order puts a result on the row of its `channel` (R-05), turned around when
         * [descending] (L-26). Place order fills rows top-down as a ranking (R-06), ranked here by
         * `place_int` since the pool may have sorted by lane; a ranking is never turned around.
         */
        fun rows(order: ResultsOrder, descending: Boolean): List<Row> {
            val laneSort = waiting || isLaneOrder(order)
            val ranked = if (laneSort) {
                lanes
            } else {
                lanes.sortedWith(
                    compareBy({
                        rank(it)
                    }, { it.channel ?: Int.MAX_VALUE }),
                )
            }
            val byRow = HashMap<Int, ResultLane>()
            ranked.forEachIndexed { idx, r ->
                // api.md §5.2: `channel` is the lane number — 0 is a lane, only null is none.
                val row = if (laneSort) r.channel?.let { it - firstLane + 1 } ?: -1 else idx + 1
                if (row in 1..numLanes) byRow[row] = r
            }
            val slots = if (laneSort && descending) (numLanes downTo 1) else (1..numLanes)
            return slots.map { i ->
                val r = byRow[i]
                if (r == null) {
                    // R-06: an unfilled rank is no lane at all. A board still waiting for its
                    // first snapshot claims nothing about its lanes (R-01).
                    Row(laneLabel = if (laneSort) label(i) else "", vacant = !waiting, slot = i)
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
                        slot = i,
                    )
                }
            }
        }

        /** L-04: row [i]'s lane number. */
        private fun label(i: Int) = (i + firstLane - 1).toString()
    }

    private var snapshot: ResultsSnapshot? = null

    fun view(): View {
        val s = snapshot ?: return View("", "", "", null, waiting = true, numLanes = numLanes, firstLane = firstLane)
        return View(
            s.event,
            s.heat,
            s.eventName,
            s.eventNameParts,
            waiting = false,
            sort = s.sort,
            lanes = s.lanes,
            numLanes = numLanes,
            firstLane = firstLane,
        )
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

    companion object {
        const val DASH = "—"

        /** A result's rank, as the web ranks it: `place_int`, else the digits of `place`, else last. */
        private fun rank(r: ResultLane): Int = r.placeInt ?: r.place.trim().toIntOrNull() ?: 99
    }
}
