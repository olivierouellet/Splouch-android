package app.splouch.core

import app.splouch.core.schedule.HeatRef
import app.splouch.core.schedule.LaneTime
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScheduleLane
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** S-24: a prelim and its final under one event number, two cards apart. */
class RoundsTests {
    private fun heat(round: String, lanes: List<Int> = listOf(4)) = ScheduleHeat(
        event = "15",
        heat = "1",
        eventName = "",
        eventNameParts = null,
        time = "",
        lanes = lanes.map { ScheduleLane(it, "N", "C", "", emptyList()) },
        round = round,
    )

    @Test fun `the round is read from every payload`() {
        val heats = ScheduleHeat.listFromJson(
            parseJsonOrNull(
                """{"heats":[{"event":15,"heat":1,"round":"final","lanes":[]},{"event":3,"heat":1,"lanes":[]}]}""",
            ),
        )!!
        assertEquals(listOf("final", ""), heats.map { it.round })
        val snap = ResultsSnapshot.fromJson(parseJsonOrNull("""{"event":"15","heat":"1","round":"prelims"}"""))!!
        assertEquals("prelims", snap.round)
    }

    @Test fun `two rounds are two cards`() {
        // A LazyColumn key used twice is a crash, which is why the schedule is asked for.
        assertNotEquals(heat("prelims").key, heat("final").key)
    }

    @Test fun `only the current round is highlighted`() {
        val v = ScheduleFilter.visible(
            listOf(heat("prelims"), heat("final")),
            ScheduleFilterState(),
            HeatRef("15", "1", "final"),
        )
        assertEquals(listOf(false, true), v.map { it.isCurrent })
    }

    @Test fun `console times land on their round`() {
        val snap = ResultsSnapshot.fromJson(
            parseJsonOrNull("""{"event":"15","heat":"1","round":"final","lanes":[{"channel":4,"time":"59.90"}]}"""),
        )!!
        val patched = LaneTime.applyConsoleTimes(listOf(heat("prelims"), heat("final")), snap)
        assertEquals(listOf("", "00:00:59.90"), patched.map { it.lanes[0].consoleTime })
    }

    /** N-08: a notification names no round; the cloud notifies about the live one, the last seeded. */
    @Test fun `a notification lands on the seeded round`() {
        assertEquals(1, ScheduleFilter.focusIndex(listOf(heat("prelims"), heat("final")), "15", "1"))
        assertEquals(0, ScheduleFilter.focusIndex(listOf(heat("prelims"), heat("final", emptyList())), "15", "1"))
        assertNull(ScheduleFilter.focusIndex(listOf(heat("")), "9", "1"))
    }
}
