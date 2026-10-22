package app.splouch.core

import app.splouch.core.board.DeltaFormat
import app.splouch.core.board.ResultsBoard
import app.splouch.core.session.ResultsOrder
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultsAndDeltaTests {
    private fun snap(json: String) = ResultsSnapshot.fromJson(parseJsonOrNull(json))!!

    private val ResultsBoard.View.byLane get() = rows(ResultsOrder.LANE, descending = false)
    private val ResultsBoard.View.byPlace get() = rows(ResultsOrder.PLACE, descending = false)

    @Test fun `P-23 - a reader's place order ranks a heat the pool sorted by lane, by place_int`() {
        val r = ResultsBoard(4)
        r.apply(
            snap(
                """{"event":"3","heat":"1","sort":"lane","lanes":[{"channel":1,"place":"","time":"","name":"DQ"},{"channel":2,"place":"2","place_int":2,"time":"2.00","name":"B"},{"channel":3,"place":"1","place_int":1,"time":"1.00","name":"A"}]}""",
            ),
        )
        val rows = r.view().byPlace
        assertEquals(listOf("A", "B", "DQ", ""), rows.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.slot })
        assertEquals("", rows[3].laneLabel)
    }

    @Test fun `P-23 - a reader's lane order puts a place-sorted heat back on its lanes`() {
        val r = ResultsBoard(4)
        r.apply(
            snap(
                """{"event":"3","heat":"1","sort":"place","lanes":[{"channel":4,"place":"1","time":"1.00","name":"D"},{"channel":2,"place":"2","time":"2.00","name":"B"}]}""",
            ),
        )
        assertEquals(listOf("", "B", "", "D"), r.view().byLane.map { it.name })
    }

    @Test fun `L-26 - lanes turned around keep their numbers and identity, a ranking never turns`() {
        val r = ResultsBoard(4, firstLane = 0)
        r.apply(
            snap(
                """{"event":"3","heat":"1","sort":"lane","lanes":[{"channel":1,"place":"2","time":"2.00","name":"B"},{"channel":3,"place":"1","time":"1.00","name":"A"}]}""",
            ),
        )
        val down = r.view().rows(ResultsOrder.LANE, descending = true)
        assertEquals(listOf("3", "2", "1", "0"), down.map { it.laneLabel })
        assertEquals(listOf(4, 3, 2, 1), down.map { it.slot })
        assertEquals(listOf("A", "", "B", ""), down.map { it.name })
        val ranked = r.view().rows(ResultsOrder.PLACE, descending = true)
        assertEquals(listOf("A", "B", "", ""), ranked.map { it.name })
    }

    @Test fun `P-23 - an absent, unknown or withdrawn stored order is by place`() {
        assertEquals(ResultsOrder.PLACE, ResultsOrder.parse(null))
        assertEquals(ResultsOrder.PLACE, ResultsOrder.parse("time"))
        assertEquals(ResultsOrder.PLACE, ResultsOrder.parse("POOL"))
        assertEquals(ResultsOrder.LANE, ResultsOrder.parse("LANE"))
        assertEquals(listOf(ResultsOrder.LANE, ResultsOrder.PLACE), ResultsOrder.entries)
    }

    @Test fun `waiting until the first snapshot`() {
        val r = ResultsBoard(4)
        assertTrue(r.view().waiting)
        assertEquals(listOf("1", "2", "3", "4"), r.view().byLane.map { it.laneLabel })
        assertTrue(r.view().byLane.all { it.time == "—" && it.place == "" })
    }

    @Test fun `lane sort - row index is the channel, gaps stay blank`() {
        val r = ResultsBoard(4)
        r.apply(
            snap(
                """{"event":"3","heat":"1","event_name":"E","lanes":[{"channel":4,"place":"1","time":"2:20.92","name":"D","club":"c"},{"channel":2,"place":" ","time":"2:21.00","name":"B"}]}""",
            ),
        )
        val v = r.view()
        assertFalse(v.waiting)
        assertEquals("3", v.event)
        assertEquals("D", v.byLane[3].name)
        assertEquals("1", v.byLane[3].place)
        assertTrue(v.byLane[3].locked)
        assertEquals("B", v.byLane[1].name)
        assertEquals("", v.byLane[1].place)
        assertEquals("", v.byLane[0].name)
        assertEquals("—", v.byLane[0].time)
        assertEquals("1", v.byLane[0].laneLabel)
        assertFalse(v.byLane[0].locked)
    }

    @Test fun `place sort fills top-down, unfilled ranks leave the lane blank`() {
        val r = ResultsBoard(4)
        r.apply(
            snap(
                """{"event":"3","heat":"1","sort":"place","lanes":[{"channel":4,"place":"1","time":"1.00","name":"D"},{"channel":1,"place":"2","time":"2.00","name":"A"}]}""",
            ),
        )
        val v = r.view()
        assertEquals(listOf("D", "A", "", ""), v.byPlace.map { it.name })
        assertEquals(listOf("4", "1", "", ""), v.byPlace.map { it.laneLabel })
        assertEquals(listOf(false, false, true, true), v.byPlace.map { it.vacant })
    }

    @Test fun `a missing time renders as a dash and an empty snapshot is ignored`() {
        val r = ResultsBoard(2)
        r.apply(snap("""{"event":"1","heat":"1","lanes":[{"channel":1,"place":"","time":"","name":"A"}]}"""))
        assertEquals("—", r.view().byLane[0].time)
        assertFalse(r.view().byLane[0].locked)
        r.apply(snap("""{"event":"9","heat":"9","lanes":[]}"""))
        assertEquals("1", r.view().event)
    }

    @Test fun `a disconnect or meet_live false wipes the board`() {
        val r = ResultsBoard(2)
        r.apply(snap("""{"event":"1","heat":"1","lanes":[{"channel":1,"place":"1","time":"1.00","name":"A"}]}"""))
        r.clear()
        assertTrue(r.view().waiting)
        assertEquals("", r.view().event)
        assertEquals("", r.view().byLane[0].name)
    }

    @Test fun `delta format`() {
        assertEquals("", DeltaFormat.text(null))
        assertEquals("-0.46", DeltaFormat.text(-0.46))
        assertEquals("+1.20", DeltaFormat.text(1.2))
        assertEquals("+0.00", DeltaFormat.text(0.0))
        assertEquals("-1:02.50", DeltaFormat.text(-62.5))
        assertEquals("", DeltaFormat.text(Double.NaN))
    }

    @Test fun `lane sort - channel 0 is lane 0 on row 1 when the pool starts at 0`() {
        val r = ResultsBoard(4, firstLane = 0)
        assertEquals(listOf("0", "1", "2", "3"), r.view().byLane.map { it.laneLabel })
        r.apply(
            snap(
                """{"event":"1","heat":"1","sort":"lane","lanes":[{"channel":0,"place":"2","time":"30.00","name":"Zed"},{"channel":3,"place":"1","time":"29.00","name":"Dee"}]}""",
            ),
        )
        val v = r.view()
        assertEquals(listOf("Zed", "", "", "Dee"), v.byLane.map { it.name })
        assertEquals(listOf("0", "1", "2", "3"), v.byLane.map { it.laneLabel })
        assertEquals(listOf(false, true, true, false), v.byLane.map { it.vacant })
    }

    @Test fun `place sort - lane 0 keeps its number, unfilled ranks have none`() {
        val r = ResultsBoard(4, firstLane = 0)
        r.apply(
            snap(
                """{"event":"1","heat":"1","sort":"place","lanes":[{"channel":3,"place":"1","time":"29.00","name":"Dee"},{"channel":0,"place":"2","time":"30.00","name":"Zed"}]}""",
            ),
        )
        val v = r.view()
        assertEquals(listOf("Dee", "Zed", "", ""), v.byPlace.map { it.name })
        assertEquals(listOf("3", "0", "", ""), v.byPlace.map { it.laneLabel })
    }

    @Test fun `a pool from lane 1 has no row for channel 0, as before`() {
        val r = ResultsBoard(2)
        r.apply(
            snap(
                """{"event":"1","heat":"1","lanes":[{"channel":0,"time":"1.00","name":"X"},{"channel":2,"time":"2.00","name":"B"}]}""",
            ),
        )
        assertEquals(listOf("", "B"), r.view().byLane.map { it.name })
        assertEquals(listOf("1", "2"), r.view().byLane.map { it.laneLabel })
    }
}
