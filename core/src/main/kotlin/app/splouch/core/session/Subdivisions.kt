package app.splouch.core.session

import app.splouch.core.schedule.SearchFold
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P-01, P-21: a state or province named in full. The organizer's province is free text —
 * `QC`, `Québec`, `Quebec` — and the platform names an ISO 3166-2 subdivision nowhere the way
 * it names a country, so the app carries the names: `subdivisions.json`, a verbatim copy of
 * the server repo's `shared/regions/subdivisions.json`. Each is the subdivision's own name in
 * its majority language, translated only where the country has two or more official languages
 * (`names`: Canada's in French). A province the table does not know is shown as sent.
 */
object Subdivisions {
    /** One subdivision: its ISO 3166-2 code, without the country, its name, and its name in each language that names it otherwise. */
    data class Entry(val code: String, val name: String, val names: Map<String, String> = emptyMap()) {
        /** `Colombie-Britannique` in French, `British Columbia` in any other language; blank [lang] is the device's. */
        fun name(lang: String): String {
            val code = (if (lang.isBlank()) Locale.getDefault() else Locale.forLanguageTag(lang)).language
            return names[code] ?: name
        }
    }

    /** [names]: country → language → code → name. */
    @Serializable
    private class File(
        val countries: Map<String, Map<String, List<String>>>,
        val names: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Country → every folded spelling (code, name, aliases) → its entry. */
    private val table: Map<String, Map<String, Entry>> by lazy {
        val raw = Subdivisions::class.java.getResourceAsStream("/subdivisions.json")
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@lazy emptyMap()
        val file = runCatching { json.decodeFromString(File.serializer(), raw) }
            .getOrNull() ?: return@lazy emptyMap()
        file.countries.entries.associate { (country, subdivisions) ->
            val translated = file.names[country].orEmpty()
            country.uppercase() to buildMap {
                subdivisions.forEach { (code, names) ->
                    val name = names.firstOrNull() ?: return@forEach
                    val entry =
                        Entry(code, name, translated.mapNotNull { (lang, n) -> n[code]?.let { lang to it } }.toMap())
                    (listOf(code) + names).forEach { put(SearchFold.fold(it), entry) }
                }
            }
        }
    }

    /** P-21: every country the table knows, ISO 3166-1 alpha-2, upper-cased. */
    val countries: Set<String> get() = table.keys

    /** P-21: every subdivision of [country] the table knows, once each. */
    fun all(country: String): List<Entry> = table[country.uppercase()].orEmpty().values.distinctBy { it.code }

    /**
     * The subdivision [province] spells in [country], matched by code or any listed spelling,
     * folded as S-09 folds; null when the table has no such one.
     */
    fun lookup(country: String, province: String): Entry? {
        val key = SearchFold.fold(province)
        if (key.isBlank()) return null
        return table[country.uppercase()]?.get(key)
    }

    /** `Québec` for `QC` in Canada, `Colombie-Britannique` for `BC` in French; anything unknown as sent. */
    fun name(country: String, province: String, lang: String = ""): String =
        lookup(country, province)?.name(lang) ?: province
}
