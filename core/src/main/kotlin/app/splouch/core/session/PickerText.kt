package app.splouch.core.session

import app.splouch.core.strings.StringTable
import app.splouch.core.wire.PickerConfig

/**
 * P-06: the one quiet line above the meets, and the full text a tap on it opens. Both are
 * this server's words (api.md §5.7 `strings`); the line falls back to the snapshot, in the
 * reader's language, for an older server that sends the full text alone.
 */
data class Disclaimer(val short: String, val full: String)

/**
 * Null until this server has sent its text (`app.md` `P-06`): no picker config yet — a first
 * launch offline — means no line, as on iOS. It is this server's words about this server's
 * results; the snapshot's copy would be another server's.
 */
fun disclaimer(config: PickerConfig?, table: StringTable): Disclaimer? {
    val full = served(config, "results_disclaimer") ?: return null
    return Disclaimer(served(config, "results_disclaimer_short") ?: table.mobile("results_disclaimer_short"), full)
}

/**
 * P-07: whether the Privacy section shows at all — only while the server reports counting on
 * (C-10). With counting off there is nothing to refuse; the stored choice is kept regardless.
 */
fun countingOffered(config: PickerConfig?): Boolean = config?.analyticsEnabled == true

/** P-07: the server's note under the toggle, while it counts. Its own words or none. */
fun privacyNote(config: PickerConfig?): String? =
    config?.takeIf { it.analyticsEnabled }?.let { served(it, "privacy_note") }

/** A word from `GET /picker/config` → `strings`; empty counts as absent, as in [StringTable]. */
private fun served(config: PickerConfig?, key: String): String? = config?.strings?.get(key)?.takeIf(String::isNotEmpty)
