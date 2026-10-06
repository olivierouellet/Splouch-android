package app.splouch.core

import app.splouch.core.schedule.LaneTime
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScheduleLane
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S-22 and S-23: which of a lane's three times its cell shows, and an official heat's gaps. */
class LaneTimeTests {
    private fun lane(
        seed: String = "00:00:31.00",
        console: String = "",
        result: String = "",
        status: String = "",
        delta: Double? = null,
        better: Boolean? = null,
    ) = ScheduleLane(3, "N", "C", seed, emptyList(), console, result, status, delta, better)

    @Test fun `the best known time wins`() {
        assertEquals(LaneTime("00:31.00", LaneTime.Kind.SEED, "time_seed"), LaneTime.of(lane()))
        assertEquals(LaneTime.Kind.CONSOLE, LaneTime.of(lane(console = "00:00:30.15"))?.kind)
        assertEquals(
            LaneTime("00:30.12", LaneTime.Kind.OFFICIAL, "time_official"),
            LaneTime.of(lane(console = "00:00:30.15", result = "00:00:30.12")),
        )
        assertEquals(
            LaneTime("DSQ", LaneTime.Kind.OFFICIAL, "status_dsq", speaksText = false),
            LaneTime.of(lane(console = "00:00:30.15", result = "00:00:30.80", status = "DSQ")),
        )
        assertNull(LaneTime.of(lane(seed = "")))
    }

    @Test fun `the hours go when there are none`() {
        assertEquals("01:02.34", LaneTime.display("00:01:02.34"))
        assertEquals("01:01:00.00", LaneTime.display("01:01:00.00"))
        assertEquals("1:02.34", LaneTime.display("1:02.34")) // a Hytek seed, as written
    }

    @Test fun `a console time is read into wire form`() {
        assertEquals("00:00:58.21", LaneTime.wire("58.21"))
        assertEquals("00:01:02.34", LaneTime.wire("1:02.34"))
        assertEquals("", LaneTime.wire(""))
        assertEquals("", LaneTime.wire("NT"))
    }

    @Test fun `the gap to the seed`() {
        assertEquals(
            LaneTime("-0.88", LaneTime.Kind.BETTER, "seed_diff"),
            LaneTime.of(lane(result = "00:00:30.12", delta = -0.88, better = true), diff = true),
        )
        assertEquals(
            LaneTime.Kind.WORSE,
            LaneTime.of(lane(result = "00:00:31.50", delta = 0.5, better = false), diff = true)?.kind,
        )
        // A disqualified swim shows what the console read.
        assertEquals(
            LaneTime("00:30.15", LaneTime.Kind.CONSOLE, "time_console"),
            LaneTime.of(lane(console = "00:00:30.15", status = "DSQ"), diff = true),
        )
        // No seed, no gap.
        assertEquals("NT", LaneTime.of(lane(seed = "", result = "00:00:30.12"), diff = true)?.text)
    }

    @Test fun `a results frame patches its heat's console times`() {
        val heats = ScheduleHeat.listFromJson(
            parseJsonOrNull("""{"heats":[{"event":1,"heat":2,"lanes":[{"lane":3,"name":"N","seed_time":""}]}]}"""),
        )!!
        val snap = ResultsSnapshot.fromJson(
            parseJsonOrNull("""{"event":"1","heat":"2","lanes":[{"channel":3,"time":"30.15"}]}"""),
        )!!
        assertEquals("00:00:30.15", LaneTime.applyConsoleTimes(heats, snap)[0].lanes[0].consoleTime)
    }

    @Test fun `the new fields decode and default`() {
        val heats = ScheduleHeat.listFromJson(
            parseJsonOrNull(
                """{"heats":[{"event":1,"heat":1,"official":true,"lanes":[{"lane":3,"name":"N",""" +
                    """"seed_time":"00:00:31.00","console_time":"00:00:30.15","result_time":"00:00:30.12",""" +
                    """"result_status":"","result_delta_seconds":-0.88,"result_delta_better":true}]},""" +
                    """{"event":1,"heat":2,"lanes":[{"lane":1,"name":"M"}]}]}""",
            ),
        )!!
        assertTrue(heats[0].official)
        assertEquals("00:00:30.12", heats[0].lanes[0].resultTime)
        assertEquals(-0.88, heats[0].lanes[0].resultDeltaSeconds)
        assertEquals(true, heats[0].lanes[0].resultDeltaBetter)
        // An older server sends none of them.
        assertFalse(heats[1].official)
        assertEquals("", heats[1].lanes[0].consoleTime)
        assertNull(heats[1].lanes[0].resultDeltaSeconds)
    }
}
