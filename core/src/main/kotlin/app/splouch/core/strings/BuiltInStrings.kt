package app.splouch.core.strings

import app.splouch.core.wire.I18nBundle
import app.splouch.core.wire.LocaleEntry
import app.splouch.core.wire.parseJsonOrNull

/**
 * The compiled-in floor of app.md T-10: `src/main/resources/i18n/<lang>.json` is the
 * body of `GET /i18n/{lang}` verbatim, and `locales.json` the body of `GET /locales`,
 * both written by `scripts/update-strings.sh` from a running server and never by hand.
 */
object BuiltInStrings {
    fun load(lang: String): I18nBundle? = read("/i18n/$lang.json")?.let { I18nBundle.fromText(it) }

    fun english(): I18nBundle? = load(StringTable.ENGLISH)

    fun locales(): List<LocaleEntry> = read("/i18n/locales.json")?.let { LocaleEntry.listFromJson(parseJsonOrNull(it)) } ?: emptyList()

    /** A table for [lang] from the built-in layers only — what a first launch draws with. */
    fun table(lang: String, cached: I18nBundle? = null): StringTable {
        val builtIn = load(lang)
        val english = if (lang == StringTable.ENGLISH) builtIn else english()
        return StringTable(lang, cached, builtIn, english)
    }

    private fun read(path: String): String? =
        BuiltInStrings::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
}
