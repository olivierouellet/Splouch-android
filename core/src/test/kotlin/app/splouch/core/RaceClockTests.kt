package app.splouch.core

import app.splouch.core.clock.RaceClock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaceClockTests {
    @Test fun `parses the one pattern and nothing looser`() {
        assertEquals(523, RaceClock.parseHundredths("5.23"))
        assertEquals(2561, RaceClock.parseHundredths("25.61"))
        assertEquals(6523, RaceClock.parseHundredths("1:05.23"))
        assertEquals(12 * 6000 + 5, RaceClock.parseHundredths("12:00.05"))
        for (bad in listOf("", "25", "25.6", "25.612", "125.00", "1:5.2", " 5.23", "5.23 ", "a", "-5.23", "1:05:23"))
            assertNull(RaceClock.parseHundredths(bad), bad)
    }

    @Test fun `formats tenths`() {
        assertEquals("5.2", RaceClock.formatTenths(523))
        assertEquals("59.9", RaceClock.formatTenths(5999))
        assertEquals("1:00.0", RaceClock.formatTenths(6000))
        assertEquals("1:02.4", RaceClock.formatTenths(6249))
        assertEquals("10:05.0", RaceClock.formatTenths(60500))
    }

    @Test fun `ticks from a re-base, freezes forward after three sync intervals`() = runTest {
        val clock = RaceClock(testScheduler.timeSource)
        assertNull(clock.reading())
        assertTrue(clock.rebase("1:00.00"))
        assertEquals(RaceClock.Reading.Ticking("1:00.0"), clock.reading())
        advanceTimeBy(1_550)
        assertEquals(RaceClock.Reading.Ticking("1:01.5"), clock.reading())
        advanceTimeBy(4_450)
        assertEquals(RaceClock.Reading.Ticking("1:06.0"), clock.reading())
        advanceTimeBy(1)
        assertEquals(RaceClock.Reading.Frozen("1:06.0"), clock.reading())
        advanceTimeBy(60_000)
        assertEquals(RaceClock.Reading.Frozen("1:06.0"), clock.reading())
    }

    @Test fun `a hard re-base never eases, a bad value is no re-base`() = runTest {
        val clock = RaceClock(testScheduler.timeSource)
        clock.rebase("10.00")
        advanceTimeBy(3_000)
        assertFalse(clock.rebase("garbage"))
        assertEquals("13.0", clock.reading()!!.text)
        clock.rebase("20.00")
        assertEquals("20.0", clock.reading()!!.text)
        clock.stop()
        assertNull(clock.reading())
        assertFalse(clock.isRunning)
    }
}
