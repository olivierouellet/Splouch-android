package app.splouch.core.strings

import app.splouch.core.wire.MeetSettings

/**
 * Column headers and header labels (app.md T-04, T-09). The app never holds the label
 * table: every column keeps the operator's word as sent — or, once the user has chosen
 * a language, that language's table from `GET /i18n/{lang}`. Those are the only two
 * sources, and nothing is layered over either: there is no per-meet override
 * (api.md §5.4).
 *
 * The one exception is T-09's control: the EVENT and HEAT headers follow the device's
 * short/long choice, taken from the table for the language in effect. That choice is
 * always one of the two — [LONG] until the user picks [SHORT] — so the operator's
 * `label_style` no longer decides where it starts.
 */
object Labels {
    const val SHORT = "short"
    const val LONG = "long"

    /** Where T-09's control starts, on every server and before the user touches it. */
    const val DEFAULT = LONG

    /** The two headers with a long form; every other column keeps its short word in both styles. */
    val WIDE_KEYS = setOf("event", "heat")

    /**
     * @param chosenLang the user's language override (T-08), null for the meet's
     * @param chosenStyle the user's style (T-09), [DEFAULT] until they choose
     * @param table strings for the language in effect
     */
    fun resolve(settings: MeetSettings, chosenLang: String?, chosenStyle: String, table: StringTable): Map<String, String> {
        val style = if (chosenStyle == SHORT) SHORT else LONG
        val base = HashMap(if (chosenLang == null) settings.labels else table.labels(SHORT))
        for (k in WIDE_KEYS) table.labels(style)[k]?.let { base[k] = it }
        return base
    }
}
