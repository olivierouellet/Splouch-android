package app.splouch.core

import app.splouch.core.board.DeltaFormat
import app.splouch.core.board.ResultsBoard
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultsAndDeltaTests {
    private fun snap(json: String) = ResultsSnapshot.fromJson(parseJsonOrNull(json))!!

    @Test fun `waiting until the first snapshot`() {
        val r = ResultsBoard(4)
        assertTrue(r.view().waiting)
        assertEquals(listOf("1", "2", "3", "4"), r.view().rows.map { it.laneLabel })
        assertTrue(r.view().rows.all { it.time == "—" && it.place == "" })
    }

    @Test fun `lane sort - row index is the channel, gaps stay blank, absent sort means lane`() {
        val r = ResultsBoard(4)
        r.apply(
            snap(
                """{"event":"3","heat":"1","event_name":"E","lanes":[{"channel":4,"place":"1","time":"2:20.92","name":"D","club":"c"},{"channel":2,"place":" ","time":"2:21.00","name":"B"}]}""",
            ),
        )
        val v = r.view()
        assertFalse(v.waiting)
        assertEquals("3", v.event)
        assertEquals("D", v.rows[3].name)
        assertEquals("1", v.rows[3].place)
        assertTrue(v.rows[3].locked)
        assertEquals("B", v.rows[1].name)
        assertEquals("", v.rows[1].place)
        assertEquals("", v.rows[0].name)
        assertEquals("—", v.rows[0].time)
        assertEquals("1", v.rows[0].laneLabel)
        assertFalse(v.rows[0].locked)
    }

    @Test fun `place sort fills top-down, unfilled ranks leave the lane blank`() {
        val r = ResultsBoard(4)
        r.apply(
            snap(
                """{"event":"3","heat":"1","sort":"place","lanes":[{"channel":4,"place":"1","time":"1.00","name":"D"},{"channel":1,"place":"2","time":"2.00","name":"A"}]}""",
            ),
        )
        val v = r.view()
        assertEquals(listOf("D", "A", "", ""), v.rows.map { it.name })
        assertEquals(listOf("4", "1", "", ""), v.rows.map { it.laneLabel })
        assertEquals(listOf(false, false, true, true), v.rows.map { it.vacant })
    }

    @Test fun `a missing time renders as a dash and an empty snapshot is ignored`() {
        val r = ResultsBoard(2)
        r.apply(snap("""{"event":"1","heat":"1","lanes":[{"channel":1,"place":"","time":"","name":"A"}]}"""))
        assertEquals("—", r.view().rows[0].time)
        assertFalse(r.view().rows[0].locked)
        r.apply(snap("""{"event":"9","heat":"9","lanes":[]}"""))
        assertEquals("1", r.view().event)
    }

    @Test fun `a disconnect or meet_live false wipes the board`() {
        val r = ResultsBoard(2)
        r.apply(snap("""{"event":"1","heat":"1","lanes":[{"channel":1,"place":"1","time":"1.00","name":"A"}]}"""))
        r.clear()
        assertTrue(r.view().waiting)
        assertEquals("", r.view().event)
        assertEquals("", r.view().rows[0].name)
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
        assertEquals(listOf("0", "1", "2", "3"), r.view().rows.map { it.laneLabel })
        r.apply(
            snap(
                """{"event":"1","heat":"1","sort":"lane","lanes":[{"channel":0,"place":"2","time":"30.00","name":"Zed"},{"channel":3,"place":"1","time":"29.00","name":"Dee"}]}""",
            ),
        )
        val v = r.view()
        assertEquals(listOf("Zed", "", "", "Dee"), v.rows.map { it.name })
        assertEquals(listOf("0", "1", "2", "3"), v.rows.map { it.laneLabel })
        assertEquals(listOf(false, true, true, false), v.rows.map { it.vacant })
    }

    @Test fun `place sort - lane 0 keeps its number, unfilled ranks have none`() {
        val r = ResultsBoard(4, firstLane = 0)
        r.apply(
            snap(
                """{"event":"1","heat":"1","sort":"place","lanes":[{"channel":3,"place":"1","time":"29.00","name":"Dee"},{"channel":0,"place":"2","time":"30.00","name":"Zed"}]}""",
            ),
        )
        val v = r.view()
        assertEquals(listOf("Dee", "Zed", "", ""), v.rows.map { it.name })
        assertEquals(listOf("3", "0", "", ""), v.rows.map { it.laneLabel })
    }

    @Test fun `a pool from lane 1 has no row for channel 0, as before`() {
        val r = ResultsBoard(2)
        r.apply(
            snap(
                """{"event":"1","heat":"1","lanes":[{"channel":0,"time":"1.00","name":"X"},{"channel":2,"time":"2.00","name":"B"}]}""",
            ),
        )
        assertEquals(listOf("", "B"), r.view().rows.map { it.name })
        assertEquals(listOf("1", "2"), r.view().rows.map { it.laneLabel })
    }
}
