package app.splouch.core.clock

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The race clock for the current heat (app.md L-12).
 *
 * One clock per heat, not one per lane. The server re-bases it with every
 * `running_time` frame and the device advances it in between, off a *monotonic*
 * time source, so a wall-clock correction mid-heat cannot move it.
 *
 * What it deliberately does not do: start or reset from a `lane_running<i>` edge,
 * ease towards a re-base, blank on silence, or tick across a suspend. `lane_running<i>`
 * only decides whether lane *i* displays it; the board owns that.
 */
class RaceClock(private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic) {

    sealed interface Reading {
        val text: String

        /** Fresh: the device is advancing the digits. */
        data class Ticking(override val text: String) : Reading

        /**
         * Three sync intervals past the last re-base with nothing from the server: frozen
         * forward, where the digits stopped, never back at the base.
         */
        data class Frozen(override val text: String) : Reading
    }

    private var baseHundredths: Int? = null
    private var baseMark: ComparableTimeMark? = null

    /** True between a re-base and [stop]. */
    val isRunning: Boolean get() = baseHundredths != null

    /**
     * Hard re-base from a `running_time` string. Returns false and changes nothing when
     * the text is not a clock value (api.md §5.1): the ticker carries on from its last base.
     */
    fun rebase(text: String): Boolean {
        val h = parseHundredths(text) ?: return false
        baseHundredths = h
        baseMark = timeSource.markNow()
        return true
    }

    /** Forgets the base. Leaves nothing on screen by itself; the caller decides what replaces the digits. */
    fun stop() {
        baseHundredths = null
        baseMark = null
    }

    fun reading(): Reading? {
        val base = baseHundredths ?: return null
        val mark = baseMark ?: return null
        val age = mark.elapsedNow()
        if (age > STALE_AFTER) return Reading.Frozen(formatTenths(base + hundredths(STALE_AFTER)))
        return Reading.Ticking(formatTenths(base + hundredths(if (age.isNegative()) Duration.ZERO else age)))
    }

    companion object {
        /** The relay forwards `running_time` at most once every ~2s (api.md §5.1). */
        val SYNC_INTERVAL: Duration = 2.seconds

        /** Silence for three sync intervals means the feed died, not the throttle. */
        val STALE_AFTER: Duration = SYNC_INTERVAL * 3

        /** One tenth: the last digit displayed. */
        val TICK: Duration = 100.milliseconds

        /** The one pattern (api.md §5.1): optional minutes, one or two second digits, two hundredths. */
        private val PATTERN = Regex("""^(?:(\d+):)?(\d{1,2})\.(\d{2})$""")

        fun parseHundredths(text: String): Int? {
            val m = PATTERN.matchEntire(text) ?: return null
            val minutes = m.groupValues[1].ifEmpty { "0" }.toIntOrNull() ?: return null
            val seconds = m.groupValues[2].toInt()
            val hundredths = m.groupValues[3].toInt()
            return minutes * 6000 + seconds * 100 + hundredths
        }

        /** `1:02.4`, `59.9`, `5.2` — tenths, never hundredths, on the phone. */
        fun formatTenths(hundredths: Int): String {
            val t = hundredths / 10
            val mm = t / 600
            val ss = (t / 10) % 60
            val d = t % 10
            return if (mm > 0) "$mm:${ss.toString().padStart(2, '0')}.$d" else "$ss.$d"
        }

        private fun hundredths(d: Duration): Int = (d.inWholeMilliseconds / 10).toInt()
    }
}
