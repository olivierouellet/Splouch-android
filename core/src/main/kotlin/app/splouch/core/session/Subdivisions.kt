package app.splouch.core.session

import app.splouch.core.schedule.SearchFold
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P-01, P-21: a state or province named in full. The organizer's province is free text —
 * `QC`, `Québec`, `Quebec` — and the platform names an ISO 3166-2 subdivision nowhere the way
 * it names a country, so the app carries the names: `subdivisions.json`, a verbatim copy of
 * the server repo's `shared/regions/subdivisions.json`. Each is the subdivision's own name in
 * its majority language, never translated. A province the table does not know is shown as sent.
 */
object Subdivisions {
    /** One subdivision: its ISO 3166-2 code, without the country, and its name. */
    data class Entry(val code: String, val name: String)

    @Serializable
    private class File(val countries: Map<String, Map<String, List<String>>>)

    private val json = Json { ignoreUnknownKeys = true }

    /** Country → every folded spelling (code, name, aliases) → its entry. */
    private val table: Map<String, Map<String, Entry>> by lazy {
        val raw = Subdivisions::class.java.getResourceAsStream("/subdivisions.json")
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@lazy emptyMap()
        val file = runCatching { json.decodeFromString(File.serializer(), raw) }
            .getOrNull() ?: return@lazy emptyMap()
        file.countries.entries.associate { (country, subdivisions) ->
            country.uppercase() to buildMap {
                subdivisions.forEach { (code, names) ->
                    val name = names.firstOrNull() ?: return@forEach
                    val entry = Entry(code, name)
                    (listOf(code) + names).forEach { put(SearchFold.fold(it), entry) }
                }
            }
        }
    }

    /**
     * The subdivision [province] spells in [country], matched by code or any listed spelling,
     * folded as S-09 folds; null when the table has no such one.
     */
    fun lookup(country: String, province: String): Entry? {
        val key = SearchFold.fold(province)
        if (key.isBlank()) return null
        return table[country.uppercase()]?.get(key)
    }

    /** `Québec` for `QC` in Canada; anything unknown as sent. */
    fun name(country: String, province: String): String = lookup(country, province)?.name ?: province
}
