package app.splouch.core.strings

import app.splouch.core.wire.MeetSettings

/**
 * Column headers and header labels (app.md T-04, T-09). The app never holds the label
 * table: it renders `settings.labels` as sent, or — once the user has chosen a language
 * or a style — the matching table from `GET /i18n/{lang}`.
 */
object Labels {
    const val SHORT = "short"
    const val LONG = "long"

    /** The two headers with a long form; every other column keeps its short word in both styles. */
    val WIDE_KEYS = setOf("event", "heat")

    /**
     * @param chosenLang the user's language override (T-08), null for the meet's
     * @param chosenStyle the user's style override (T-09), null for the operator's
     * @param table strings for the language in effect, used only when an override is set
     */
    fun resolve(settings: MeetSettings, chosenLang: String?, chosenStyle: String?, table: StringTable): Map<String, String> {
        if (chosenLang == null && chosenStyle == null) return settings.labels
        val style = chosenStyle ?: settings.labelStyle ?: SHORT
        val lang = chosenLang ?: settings.locale ?: table.lang
        val base = HashMap(table.labels(SHORT))
        if (style == LONG) for (k in WIDE_KEYS) table.labels(LONG)[k]?.let { base[k] = it }
        // This Pi's custom wording, which the cloud cannot see in its locale files
        // (api.md §5.4). A long override on a narrow column is ignored, as in the bundled table.
        settings.labelOverrides[lang]?.let { byStyle ->
            byStyle[SHORT]?.let { base.putAll(it) }
            if (style == LONG) byStyle[LONG]?.forEach { (k, v) -> if (k in WIDE_KEYS) base[k] = v }
        }
        return base
    }
}
