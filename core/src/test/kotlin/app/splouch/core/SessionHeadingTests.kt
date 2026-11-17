package app.splouch.core

import app.splouch.core.schedule.LaneTime
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScheduleLane
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** S-26: a heading where the session changes. S-27: a seed from another course. */
class SessionHeadingTests {
    private fun heat(event: String, session: Int, name: String = "") = ScheduleHeat(
        event = event,
        heat = "1",
        eventName = "",
        eventNameParts = null,
        time = "",
        lanes = listOf(ScheduleLane(4, "N", "C", "", emptyList())),
        session = session,
        sessionName = name,
    )

    private fun visible(heats: List<ScheduleHeat>) = ScheduleFilter.visible(heats, ScheduleFilterState(), null)

    @Test fun `the session and the seed course are read`() {
        val h = ScheduleHeat.listFromJson(
            parseJsonOrNull(
                """{"heats":[{"event":3,"heat":1,"session":2,"session_name":"Samedi pm",""" +
                    """"lanes":[{"lane":4,"seed_time":"00:01:01.00","seed_course":"LCM"}]}]}""",
            ),
        )!![0]
        assertEquals(2, h.session)
        assertEquals("Samedi pm", h.sessionName)
        assertEquals("LCM", h.lanes[0].seedCourse)
        // An older server sends none: no heading, the seed alone.
        val old = ScheduleHeat.listFromJson(
            parseJsonOrNull("""{"heats":[{"event":3,"heat":1,"lanes":[{"lane":4}]}]}"""),
        )!![0]
        assertEquals(0, old.session)
        assertEquals("", old.sessionName)
        assertEquals("", old.lanes[0].seedCourse)
    }

    @Test fun `a heading where the session changes`() {
        val v = visible(
            listOf(heat("1", 1, "Samedi am"), heat("2", 1, "Samedi am"), heat("15", 2), heat("16", 0)),
        )
        assertEquals(
            listOf("Samedi am", null, "Séance 2", null),
            v.indices.map { ScheduleFilter.sessionHeading(v, it, "Séance {n}") },
        )
    }

    @Test fun `a filter moves the heading to the first heat left`() {
        val v = visible(listOf(heat("2", 1, "Samedi am"), heat("3", 1, "Samedi am"))).drop(1)
        assertEquals("Samedi am", ScheduleFilter.sessionHeading(v, 0, ""))
    }

    @Test fun `a seed from another course shows it and no gap`() {
        var lane = ScheduleLane(4, "N", "C", "00:01:01.00", emptyList(), seedCourse = "LCM")
        assertEquals("01:01.00 LCM", LaneTime.of(lane)?.text)
        // Not official yet: the console's gap would be to a long-course seed.
        lane = lane.copy(consoleTime = "00:00:59.00")
        assertEquals("01:01.00 LCM", LaneTime.of(lane, diff = true)?.text)
        assertNotEquals("01:01.00 LCM", LaneTime.of(lane.copy(seedCourse = ""), diff = true)?.text)
    }
}
