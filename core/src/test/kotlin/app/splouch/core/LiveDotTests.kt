package app.splouch.core

import app.splouch.core.session.LiveDot
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** P-03's live dot: every row breathes at one pace, but rows close together never together. */
class LiveDotTests {
    private val cycle = (0 until 170).map { it / 100.0 }

    /** The widest gap between two rows' breaths over one full cycle. */
    private fun spread(a: Int, b: Int) = cycle.maxOf { abs(LiveDot.breath(it, a) - LiveDot.breath(it, b)) }

    @Test fun `a breath runs from full to faint and back`() {
        val samples = cycle.map { LiveDot.breath(it, 0) }
        assertTrue(samples.min() in 0.0..0.01)
        assertTrue(samples.max() in 0.99..1.0)
        assertTrue(abs(LiveDot.breath(0.0, 3) - LiveDot.breath(LiveDot.PERIOD_SECONDS, 3)) < 1e-9)
    }

    @Test fun `rows close together are never in step`() {
        for (rank in 0 until 60) {
            assertTrue(spread(rank, rank + 1) > 0.9)
            assertTrue(spread(rank, rank + 2) > 0.6)
        }
    }
}
