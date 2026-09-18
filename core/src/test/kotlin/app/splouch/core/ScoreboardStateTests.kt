package app.splouch.core

import app.splouch.core.board.LapDirection
import app.splouch.core.board.LapSettings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreboardStateTests {
    private fun frame(json: String) = ScoreboardFrame.fromJson(parseJsonOrNull(json))!!

    private fun TestScope.board(lanes: Int = 4): ScoreboardState =
        ScoreboardState(lanes, RaceClock(testScheduler.timeSource)).also { it.onConnect(); it.setMeetLive(true) }

    private fun ScoreboardState.lane(i: Int) = view().lanes[i - 1]

    // L-23. The lap is read off the view the screen is handed, which is the whole point: it is
    // a function of merged state and of no transition at all.
    private fun ScoreboardState.lap(i: Int, settings: LapSettings) = view().let { it.lap(it.lanes[i - 1], settings) }

    private val up = LapSettings(show = true, direction = LapDirection.UP)
    private val down = LapSettings(show = true, direction = LapDirection.DOWN)

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

    // ── L-23: lap counts in the delta cell ───────────────────────────────────

    @Test fun `L-23 counting up waits for the first wall, and says nothing when the setting is off`() = runTest {
        val b = board()
        // A 200m in a 25m pool, padded at one end only: the venue's two numbers, together.
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann"}"""))
        // A column of noughts under a start list is noise, so counting up shows nothing yet.
        assertNull(b.lap(1, up))
        b.apply(frame("""{"lane_splits1":2}"""))
        assertEquals("2", b.lap(1, up)?.text)
        assertFalse(b.lap(1, up)!!.isFinal)
        // And nothing at all while `show_laps` is off — the flag that ships off.
        assertNull(b.lap(1, LapSettings.OFF))
    }

    @Test fun `L-23 counting down shows the whole distance before anyone has swum, and an empty lane shows nothing`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_name2":" "}"""))
        // Counting down has the whole race to report from the moment the heat loads.
        assertEquals("8", b.lap(1, down)?.text)
        // But it needs a swimmer to say it about: an empty lane in a short heat must not
        // advertise eight lengths nobody is swimming — in either direction.
        assertNull(b.lap(2, down))
        assertNull(b.lap(2, up))
        b.apply(frame("""{"lane_splits1":2}"""))
        assertEquals("6", b.lap(1, down)?.text)
        // Clamped at 0: a console that over-counts reads as the last length, never a negative.
        b.apply(frame("""{"lane_splits1":11}"""))
        assertEquals("0", b.lap(1, down)?.text)
    }

    @Test fun `L-23 the delta reclaims the cell at the finish, and so does a place with no delta ever`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_name2":"Bo","lane_splits1":6,"lane_splits2":6}"""))
        assertEquals("6", b.lap(1, up)?.text)
        // Lane 1 finishes with a seed to measure against: the delta takes the cell back.
        b.apply(frame("""{"lane_delta_seconds1":-0.4,"lane_delta_better1":true}"""))
        assertNull(b.lap(1, up))
        // Lane 2 has no seed time, so no delta will ever come. The place is what ends the lap —
        // waiting for a delta would leave it under a finished swim for the rest of the heat.
        assertEquals("6", b.lap(2, up)?.text)
        b.apply(frame("""{"lane_place2":"2"}"""))
        assertNull(b.lap(2, up))
        // A space is no place (L-13's trim), so the lap is still the cell's.
        b.apply(frame("""{"lane_place2":" "}"""))
        assertEquals("6", b.lap(2, up)?.text)
    }

    @Test fun `L-23 expected_splits 0 falls back to counting up and has no final stretch`() = runTest {
        val b = board()
        // An event whose meet file carries no distance: there is nothing to count down from.
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":0,"split_step":1,"lane_name1":"Ann"}"""))
        // Counting down cannot start the heat off either — with no total it *is* counting up.
        assertNull(b.lap(1, down))
        b.apply(frame("""{"lane_splits1":3}"""))
        assertEquals("3", b.lap(1, down)?.text)
        assertEquals("3", b.lap(1, up)?.text)
        assertFalse(b.lap(1, down)!!.isFinal)
    }

    @Test fun `L-23 the final stretch fires at plus split_step, never plus 1`() = runTest {
        // Touchpads at one end only: the count arrives 2, 4, 6 and never lands on an odd
        // length, so a `+ 1` test would never fire on exactly the pool where the deck can
        // least easily tell. It covers the last two lengths here, and that is the pool.
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_splits1":4}"""))
        assertFalse(b.lap(1, up)!!.isFinal)   // 4 + 2 = 6, still two lengths out
        b.apply(frame("""{"lane_splits1":6}"""))
        assertTrue(b.lap(1, up)!!.isFinal)    // 6 + 2 >= 8 — `+ 1` would have said 7 and missed
        assertEquals("6", b.lap(1, up)?.text) // the number stays; only the colour moves
        assertTrue(b.lap(1, down)!!.isFinal)
        assertEquals("2", b.lap(1, down)?.text)

        // Both ends padded, and the same test lands a length later.
        val c = board()
        c.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":1,"lane_name1":"Ann","lane_splits1":6}"""))
        assertFalse(c.lap(1, up)!!.isFinal)
        c.apply(frame("""{"lane_splits1":7}"""))
        assertTrue(c.lap(1, up)!!.isFinal)
    }

    @Test fun `L-23 a phone joining mid-heat reads the replay alone, with no edge in it`() = runTest {
        // api.md §3: the cached snapshot arrives as one frame with every transition already
        // behind it. Anything keyed to "when X changed" would read this as a heat in which
        // nothing ever happened.
        val b = board()
        b.apply(frame("""{"current_event":"4","current_heat":"2","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_splits1":6,"lane_running1":true,"running_time":"1:02.50"}"""))
        assertEquals("6", b.lap(1, up)?.text)
        assertTrue(b.lap(1, up)!!.isFinal)
        assertEquals("2", b.lap(1, down)?.text)
    }

    @Test fun `L-23 a heat change empties the cell, and a reconnect drops the venue numbers`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_splits1":6}"""))
        assertEquals("6", b.lap(1, up)?.text)
        // L-13 blanks the row, and the lane sharing that cell does not wait for the server to
        // say so — even though the console's zeros land in the very same frame.
        b.apply(frame("""{"current_heat":"2","lane_name1":"Bo"}"""))
        assertEquals(0, b.lane(1).splits)
        assertNull(b.lap(1, up))
        // The countdown has a fresh race to report, and reads the whole distance again.
        assertEquals("8", b.lap(1, down)?.text)

        // A reconnect puts all three inputs back to "nothing known": a stale `expected_splits`
        // would otherwise count down from a distance this meet does not swim.
        b.apply(frame("""{"lane_splits1":4}"""))
        b.onConnect()
        assertEquals(0, b.view().expectedSplits)
        assertEquals(1, b.view().splitStep)
        assertEquals(0, b.lane(1).splits)
        assertNull(b.lap(1, up))
        assertNull(b.lap(1, down))
    }

    @Test fun `L-23 a reset clears the lap count off the screen with the rest of the board`() = runTest {
        // api.md §2.2: everything on this board belongs to something that is over.
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_splits1":6,"lane_time1":"1:02.50"}"""))
        assertEquals("6", b.lap(1, up)?.text)
        b.reset()
        assertNull(b.lap(1, up))
        assertNull(b.lap(1, down))
        assertEquals(0, b.lane(1).splits)
        assertEquals(0, b.view().expectedSplits)
        assertEquals(1, b.view().splitStep)
        // And the cells with it — unlike a reconnect, nothing is about to replay over them.
        assertEquals("", b.lane(1).name)
        assertEquals("", b.lane(1).time)
        assertEquals("", b.view().currentEvent)
        assertFalse(b.view().clockRunning)
    }

    @Test fun `L-23 a split_step of 0 or a negative count cannot fire the final stretch early`() = runTest {
        val b = board()
        b.apply(frame("""{"current_event":"1","current_heat":"1","expected_splits":8,"split_step":0,"lane_name1":"Ann","lane_splits1":7}"""))
        // A step of 0 would make `splits + step >= expected` a length-early test at 8, and
        // never fire at all below it; floored at 1, lane 1 is on the final stretch at 7.
        assertEquals(1, b.view().splitStep)
        assertTrue(b.lap(1, up)!!.isFinal)
        b.apply(frame("""{"lane_splits1":-3}"""))
        assertEquals(0, b.lane(1).splits)
        assertNull(b.lap(1, up))
    }
}
