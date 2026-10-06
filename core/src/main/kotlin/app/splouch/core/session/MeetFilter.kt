package app.splouch.core.session

import app.splouch.core.schedule.SearchFold
import app.splouch.core.wire.MeetSummary
import java.text.Collator
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P-21: the picker's own filter — by country, state/province and club — kept by the app
 * across launches and servers. A spectator follows one region or club for a season, so
 * unlike the schedule's (S-20) it is stored.
 *
 * Several values per facet: a meet passes a facet when it holds any of its values (OR), and
 * the filter when it passes every active facet (AND). A meet whose field is empty fails that
 * facet while it is active.
 */
@Serializable
data class MeetFilter(
    /** ISO 3166-1 alpha-2, upper-cased. */
    val countries: Set<String> = emptySet(),
    val provinces: Set<Province> = emptySet(),
    /** The organizer as first chosen; compared folded, as S-09 compares a club. */
    val clubs: Set<String> = emptySet(),
) {
    /**
     * A state or province is only one with its country: `ON` is not unique the world over,
     * and the sheet names it with its country.
     */
    @Serializable
    data class Province(val country: String, val name: String) {
        /** What two spellings of one province share. */
        internal val key: String get() = country.uppercase() + "/" + SearchFold.fold(name)

        /** `QC, Canada`, as P-01 names a meet's region. */
        fun label(lang: String): String =
            listOf(name, countryName(country, lang)).filter { it.isNotBlank() }.joinToString(", ")
    }

    val isActive: Boolean get() = countries.isNotEmpty() || provinces.isNotEmpty() || clubs.isNotEmpty()

    fun matches(m: MeetSummary): Boolean {
        if (countries.isNotEmpty() && m.country.uppercase() !in countries) return false
        if (provinces.isNotEmpty()) {
            val key = Province(m.country, m.province).key
            if (m.province.isBlank() || provinces.none { it.key == key }) return false
        }
        if (clubs.isNotEmpty()) {
            val key = SearchFold.fold(m.organizer)
            if (key.isBlank() || clubs.none { SearchFold.fold(it) == key }) return false
        }
        return true
    }

    /** The meets the filter leaves, in the server's order. */
    fun apply(meets: List<MeetSummary>): List<MeetSummary> = if (isActive) meets.filter(::matches) else meets

    fun has(country: String): Boolean = country.uppercase() in countries
    fun has(province: Province): Boolean = provinces.any { it.key == province.key }
    fun hasClub(club: String): Boolean = SearchFold.fold(club).let { key -> clubs.any { SearchFold.fold(it) == key } }

    /**
     * Taking a country away takes its provinces with it: the sheet no longer lists them, and
     * kept they would hide every meet of the countries left.
     */
    fun toggleCountry(country: String): MeetFilter {
        val code = country.uppercase()
        return if (code in countries) {
            copy(
                countries = countries - code,
                provinces = provinces.filterTo(mutableSetOf()) {
                    it.country.uppercase() !=
                        code
                },
            )
        } else {
            copy(countries = countries + code)
        }
    }

    fun toggleProvince(province: Province): MeetFilter {
        val held = provinces.firstOrNull { it.key == province.key }
        return copy(
            provinces = if (held !=
                null
            ) {
                provinces - held
            } else {
                provinces + province.copy(country = province.country.uppercase())
            },
        )
    }

    fun toggleClub(club: String): MeetFilter {
        val key = SearchFold.fold(club)
        val held = clubs.firstOrNull { SearchFold.fold(it) == key }
        return copy(clubs = if (held != null) clubs - held else clubs + club)
    }

    /**
     * What the sheet offers: the values the list holds, plus any chosen value it no longer
     * holds, so it can still be unchecked. Provinces only of the chosen countries once one is
     * chosen. Each sorted by what the reader sees, in [lang].
     */
    data class Options(val countries: List<String>, val provinces: List<Province>, val clubs: List<String>)

    fun options(meets: List<MeetSummary>, lang: String): Options {
        val reader = if (lang.isBlank()) Locale.getDefault() else Locale.forLanguageTag(lang)
        val collator = Collator.getInstance(reader).apply { strength = Collator.SECONDARY }

        val codes = (meets.map { it.country.uppercase() }.filter { it.isNotBlank() } + countries).toSet()

        val provinceByKey = LinkedHashMap<String, Province>()
        provinces.forEach { provinceByKey[it.key] = it }
        meets.filter { it.province.isNotBlank() }.forEach {
            val p = Province(it.country.uppercase(), it.province)
            provinceByKey.putIfAbsent(p.key, p)
        }
        val shownProvinces = provinceByKey.values.filter { countries.isEmpty() || it.country.uppercase() in countries }

        val clubByKey = LinkedHashMap<String, String>()
        clubs.forEach { clubByKey[SearchFold.fold(it)] = it }
        meets.filter {
            it.organizer.isNotBlank()
        }.forEach { clubByKey.putIfAbsent(SearchFold.fold(it.organizer), it.organizer) }

        return Options(
            countries = codes.sortedWith(compareBy(collator) { countryName(it, lang) }),
            provinces = shownProvinces.sortedWith(
                compareBy(collator) {
                    it.name + " " + countryName(it.country, lang)
                },
            ),
            clubs = clubByKey.values.sortedWith(collator),
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(filter: MeetFilter): String = json.encodeToString(serializer(), filter)

        /** A stored filter, or none: one that no longer decodes costs the filter, never the rest. */
        fun decode(raw: String?): MeetFilter =
            raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: MeetFilter()
    }
}
