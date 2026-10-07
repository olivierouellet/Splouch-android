package app.splouch.core.session

import kotlin.math.PI
import kotlin.math.cos

/** P-03: how a live meet's dot breathes in the list. */
object LiveDot {
    /** One breath, full → faint → full. */
    const val PERIOD_SECONDS = 1.7

    /**
     * Where the row at [rank] in the list is in its breath at [seconds]: 0 full, 1 faint. Each row
     * is set a golden-ratio step of the cycle past the one above it, so the list looks
     * unsynchronised yet no two rows close together ever breathe together: neighbours sit at
     * least 0.38 of a breath apart, rows two apart 0.24.
     */
    fun breath(seconds: Double, rank: Int): Double {
        val offset = (rank * 0.61803398875) % 1.0
        val phase = seconds / PERIOD_SECONDS + offset
        return (1 - cos(phase * 2 * PI)) / 2
    }
}
