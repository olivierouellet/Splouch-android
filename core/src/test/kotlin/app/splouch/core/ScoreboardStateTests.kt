package app.splouch.core

import app.splouch.core.board.ScoreboardState
import app.splouch.core.board.ScoreboardState.TimeStyle
import app.splouch.core.clock.RaceClock
import app.splouch.core.wire.ScoreboardFrame
import app.splouch.core.wire.parseJsonOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardStateTests {
    private fun frame(json: String) = ScoreboardFrame.fromJson(parseJsonOrNull(json))!!

    private fun TestScope.board(lanes: Int = 4): ScoreboardState =
        ScoreboardState(lanes, RaceClock(testScheduler.timeSource)).also { it.onConnect(); it.setMeetLive(true) }

    private fun ScoreboardState.lane(i: Int) = view().lanes[i - 1]

    @Test fun `frames are partial - merge, never replace`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"3","current_heat":"1","lane_name1":"Ann","lane_club1":"C"}"""))
        b.apply(frame("""{"lane_time1":"25.61"}"""))
        assertEquals("Ann", b.lane(1).name)
        assertEquals("C", b.lane(1).club)
        assertEquals("25.61", b.lane(1).time)
        assertEquals("3", b.view().currentEvent)
    }

    @Test fun `the first event and heat after a connect are a baseline, not a change`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"3","current_heat":"2","lane_time1":"25.61","lane_place1":"1"}"""))
        assertEquals("25.61", b.lane(1).time)
        assertEquals("1", b.lane(1).place)
        // heat first seen on a later frame is still a baseline for the heat
        val c = board()
        c.apply(frame("""{"current_event":"3","lane_time1":"25.61"}"""))
        c.apply(frame("""{"current_heat":"2"}"""))
        assertEquals("25.61", c.lane(1).time)
    }

    @Test fun `an event or heat change blanks times, deltas and places`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"3","current_heat":"1","lane_time1":"25.61","lane_place1":"1","lane_delta_seconds1":-0.4,"lane_delta_better1":true,"lane_name1":"Ann"}"""))
        b.apply(frame("""{"current_heat":"2","lane_name1":"Bob","lane_time1":"1.00"}"""))
        assertEquals("", b.lane(1).time)
        assertEquals("", b.lane(1).place)
        assertEquals(null, b.lane(1).deltaSeconds)
        assertEquals("Bob", b.lane(1).name)
        assertEquals("2", b.view().currentHeat)
        // compared as strings: "03" is a change from "3"
        b.apply(frame("""{"lane_time1":"2.00"}"""))
        b.apply(frame("""{"current_event":"03"}"""))
        assertEquals("", b.lane(1).time)
    }

    @Test fun `a lane running on the previous frame keeps the times as results`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"3","current_heat":"1"}"""))
        b.apply(frame("""{"lane_running1":true,"running_time":"10.00"}"""))
        b.apply(frame("""{"lane_running1":false,"lane_time1":"25.61","lane_place1":"1","current_heat":"2"}"""))
        assertEquals("25.61", b.lane(1).time)
        assertEquals("1", b.lane(1).place)
        assertFalse(b.view().clockRunning)
        // the next change moves on
        b.apply(frame("""{"current_heat":"3"}"""))
        assertEquals("", b.lane(1).time)
    }

    @Test fun `a lane running on this frame outranks the change and shows the clock`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"3","current_heat":"1","lane_time2":"9.99"}"""))
        b.apply(frame("""{"current_heat":"2","lane_running1":true,"running_time":"3.00"}"""))
        assertEquals("3.0", b.lane(1).time)
        assertEquals("9.99", b.lane(2).time)
        assertTrue(b.view().clockRunning)
    }

    @Test fun `running lanes show one clock, ticked by the device, re-based by the server`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_running1":true,"lane_running2":true,"lane_running3":false,"running_time":"1:00.00"}"""))
        assertEquals("1:00.0", b.lane(1).time)
        assertEquals("1:00.0", b.lane(2).time)
        assertEquals("", b.lane(3).time)
        advanceTimeBy(1_200)
        b.tick()
        assertEquals("1:01.2", b.lane(1).time)
        assertEquals("1:01.2", b.lane(2).time)
        b.apply(frame("""{"running_time":"1:05.00"}"""))
        assertEquals("1:05.0", b.lane(1).time)
        assertEquals(TimeStyle.RUNNING, b.lane(1).timeStyle)
        assertFalse(b.lane(1).pulsing)
    }

    @Test fun `a split freezes the lane, never the clock, and plays the locked edge`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_running1":true,"lane_running2":true,"running_time":"10.00"}"""))
        advanceTimeBy(500)
        b.apply(frame("""{"lane_running1":false,"lane_time1":"10.52","running_time":"10.50"}"""))
        assertEquals("10.52", b.lane(1).time)
        assertEquals(TimeStyle.LOCKED, b.lane(1).timeStyle)
        assertEquals(1, b.lane(1).lockEdge)
        assertEquals("10.5", b.lane(2).time)
        assertTrue(b.view().clockRunning)
        // a stale split in a later frame does not stamp over a running lane's clock
        b.apply(frame("""{"lane_time2":"0.00"}"""))
        assertEquals("10.5", b.lane(2).time)
        // running again cancels the lock and the edge counts once per stop
        b.apply(frame("""{"lane_running1":true,"running_time":"11.00"}"""))
        assertEquals(TimeStyle.RUNNING, b.lane(1).timeStyle)
        b.apply(frame("""{"lane_running1":false,"lane_time1":"11.30"}"""))
        assertEquals(2, b.lane(1).lockEdge)
    }

    @Test fun `no lane running stops the clock, a lane edge alone never starts it`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_running1":true}"""))
        assertFalse(b.view().clockRunning)
        assertTrue(b.lane(1).pulsing)      // joined mid-heat: pulse until the first re-base
        b.apply(frame("""{"running_time":"5.00"}"""))
        assertFalse(b.lane(1).pulsing)
        b.apply(frame("""{"lane_running1":false,"lane_time1":"5.20"}"""))
        assertFalse(b.view().clockRunning)
    }

    @Test fun `silence for three sync intervals freezes forward and falls back to the pulse`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_running1":true,"running_time":"10.00"}"""))
        advanceTimeBy(5_900)
        b.tick()
        assertEquals("15.9", b.lane(1).time)
        advanceTimeBy(200)
        b.tick()
        assertEquals("16.0", b.lane(1).time)
        assertFalse(b.view().clockRunning)
        assertTrue(b.lane(1).pulsing)
        advanceTimeBy(10_000)
        b.tick()
        assertEquals("16.0", b.lane(1).time)
        // the next re-base brings it back
        b.apply(frame("""{"running_time":"30.00"}"""))
        assertEquals("30.0", b.lane(1).time)
        assertFalse(b.lane(1).pulsing)
    }

    @Test fun `meet_live false or a disconnect stops every clock and holds the last value`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_running1":true,"running_time":"10.00"}"""))
        b.setMeetLive(false)
        assertFalse(b.view().clockRunning)
        assertEquals("10.0", b.lane(1).time)
        assertFalse(b.lane(1).pulsing)   // not live: never animate
        b.setMeetLive(true)
        b.apply(frame("""{"running_time":"12.00"}"""))
        b.onDisconnect()
        assertFalse(b.view().meetLive)
        assertEquals("12.0", b.lane(1).time)
    }

    @Test fun `a garbage running_time is no re-base and the ticker carries on`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_running1":true,"running_time":"10.00"}"""))
        advanceTimeBy(1_000)
        b.apply(frame("""{"running_time":"??"}"""))
        b.tick()
        assertEquals("11.0", b.lane(1).time)
        assertTrue(b.view().clockRunning)
    }

    @Test fun `a reconnect resets the baseline so the replay is not read as a change`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_time1":"9.00"}"""))
        b.onConnect()
        b.apply(frame("""{"current_event":"2","current_heat":"1","lane_time1":"9.50"}"""))
        assertEquals("9.50", b.lane(1).time)
    }

    @Test fun `places are trimmed so a space is no place, and background stops the clock`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","lane_place1":" ","lane_place2":"2 ","lane_running3":true,"running_time":"1.00"}"""))
        assertEquals("", b.lane(1).place)
        assertEquals("2", b.lane(2).place)
        b.stopClock()
        assertFalse(b.view().clockRunning)
        assertEquals("1.0", b.lane(3).time)
        assertTrue(b.lane(3).pulsing)
    }

    @Test fun `names version bumps only on a frame carrying a lane_name`() = runTest {
        val b = board()
        val v0 = b.view().namesVersion
        b.apply(frame("""{"lane_time1":"1.00","lane_name_alt1":"x"}"""))
        assertEquals(v0, b.view().namesVersion)
        b.apply(frame("""{"lane_name1":"Ann"}"""))
        assertEquals(v0 + 1, b.view().namesVersion)
    }
}
