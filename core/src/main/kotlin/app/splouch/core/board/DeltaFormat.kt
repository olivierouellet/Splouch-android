package app.splouch.core.board

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Signed delta versus seed from `lane_delta_seconds<i>` / `delta_seconds` (api.md §5.1,
 * §5.2), formatted as the server's own HTML delta is: `±s.hh`, switching to `±m:ss.hh`
 * past a minute. Empty when there is no delta.
 */
object DeltaFormat {
    fun text(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite()) return ""
        var h = (seconds * 100).roundToLong()
        val sign = if (h < 0) "-" else "+"
        h = abs(h)
        val minutes = h / 6000
        val secs = (h / 100) % 60
        val frac = (h % 100).toString().padStart(2, '0')
        return if (minutes > 0) "$sign$minutes:${secs.toString().padStart(2, '0')}.$frac" else "$sign$secs.$frac"
    }
}
