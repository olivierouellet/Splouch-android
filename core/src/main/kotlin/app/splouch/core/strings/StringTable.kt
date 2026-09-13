package app.splouch.core.strings

import app.splouch.core.wire.I18nBundle

/**
 * String resolution for one chosen language (app.md T-10), first hit wins:
 *
 * ```
 * key → cached server value → built-in value → built-in English → the key's own name
 * ```
 *
 * All three layers have the shape of `GET /i18n/{lang}`, so this is a lookup order, not a
 * format conversion. The server already merges English per key; repeating it here only
 * matters when the app is newer than its server.
 */
class StringTable(
    val lang: String,
    private val cached: I18nBundle?,
    private val builtIn: I18nBundle?,
    private val english: I18nBundle?,
) {
    private val layers: List<I18nBundle> = listOfNotNull(cached, builtIn, english)

    /** The app's own chrome — tab names, empty states, filter UI (T-05). */
    fun mobile(key: String): String = resolve(key) { it.mobile }

    fun display(key: String): String = resolve(key) { it.display }

    /** One label table (`short` or `long`), layered per key across the three sources. */
    fun labels(style: String): Map<String, String> = merged { it.labels[style] ?: emptyMap() }

    /** The vocabulary an `event_name_parts` composes against (T-11). */
    val eventVocab: Map<String, String> get() = merged { it.eventName }

    private fun resolve(key: String, section: (I18nBundle) -> Map<String, String>): String {
        for (layer in layers) section(layer)[key]?.let { return it }
        return key
    }

    private fun merged(section: (I18nBundle) -> Map<String, String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (layer in layers.asReversed()) out.putAll(section(layer))
        return out
    }

    companion object {
        const val ENGLISH = "en"
        val EMPTY = StringTable(ENGLISH, null, null, null)
    }
}
