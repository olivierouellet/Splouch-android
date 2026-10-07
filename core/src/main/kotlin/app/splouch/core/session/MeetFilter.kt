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
 * Places, then clubs. A meet is in the chosen places when it is in a chosen province, or in a
 * chosen country none of whose provinces is chosen: a province narrows its own country only
 * (Canada, Québec and the United States are Québec and the whole United States). Of the clubs,
 * any one will do. A meet passes when it passes both, each only while something in it is
 * chosen. A meet whose field is empty holds none of that field's values.
 */
@Serializable
data class MeetFilter(
    /** ISO 3166-1 alpha-2, upper-cased. */
    val countries: Set<String> = emptySet(),
    val provinces: Set<Province> = emptySet(),
    /** The organizer as first chosen, or letters the spectator typed; compared by [clubKey]. */
    val clubs: Set<String> = emptySet(),
) {
    /**
     * A state or province is only one with its country: `ON` is not unique the world over,
     * and the sheet names it with its country.
     */
    @Serializable
    data class Province(val country: String, val name: String) {
        /**
         * What two spellings of one province share: its code when the app knows it, so `QC`
         * and `Québec` are one choice; else the folded text.
         */
        internal val key: String get() = Subdivisions.lookup(country, name)
            ?.let { country.uppercase() + "#" + it.code }
            ?: (country.uppercase() + "/" + SearchFold.fold(name))

        /** `Québec, Canada`, as P-01 names a meet's region. */
        fun label(lang: String): String =
            listOf(Subdivisions.name(country, name, lang), countryName(country, lang)).filter {
                it.isNotBlank()
            }.joinToString(", ")
    }

    val isActive: Boolean get() = countries.isNotEmpty() || provinces.isNotEmpty() || clubs.isNotEmpty()

    fun matches(m: MeetSummary): Boolean {
        if (countries.isNotEmpty() || provinces.isNotEmpty()) {
            val code = m.country.uppercase()
            val inProvince = m.province.isNotBlank() && has(Province(code, m.province))
            val inCountry = code.isNotBlank() && code in countries && provinces.none { it.country.uppercase() == code }
            if (!inProvince && !inCountry) return false
        }
        if (clubs.isNotEmpty() && (clubKey(m.organizer).isEmpty() || !hasClub(m.organizer))) return false
        return true
    }

    /** The meets the filter leaves, in the server's order. */
    fun apply(meets: List<MeetSummary>): List<MeetSummary> = if (isActive) meets.filter(::matches) else meets

    fun has(country: String): Boolean = country.uppercase() in countries
    fun has(province: Province): Boolean = provinces.any { it.key == province.key }
    fun hasClub(club: String): Boolean = clubKey(club).let { key -> clubs.any { clubKey(it) == key } }

    /** Taking a country away takes its provinces with it: the sheet no longer lists them. */
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
        val key = clubKey(club)
        val held = clubs.firstOrNull { clubKey(it) == key }
        return copy(clubs = if (held != null) clubs - held else clubs + club)
    }

    /**
     * Chooses the club whose official letters the spectator typed; letters already chosen, or
     * none left once cleaned, change nothing.
     */
    fun addClubLetters(typed: String): MeetFilter {
        val letters = clubLetters(typed)
        return if (letters.isEmpty() || hasClub(letters)) this else copy(clubs = clubs + letters)
    }

    /**
     * What the sheet offers: clubs the list holds; every country and province the app knows,
     * plus any other the list holds; plus any chosen value, so it can still be unchecked.
     * Provinces only of the chosen countries once one is chosen, and any chosen one. Each sorted by what the
     * reader sees, in [lang].
     */
    data class Options(val countries: List<String>, val provinces: List<Province>, val clubs: List<String>)

    fun options(meets: List<MeetSummary>, lang: String): Options {
        val reader = if (lang.isBlank()) Locale.getDefault() else Locale.forLanguageTag(lang)
        val collator = Collator.getInstance(reader).apply { strength = Collator.SECONDARY }

        val codes = (meets.map { it.country.uppercase() }.filter { it.isNotBlank() } + countries).toSet() +
            Subdivisions.countries

        val provinceByKey = LinkedHashMap<String, Province>()
        provinces.forEach { provinceByKey[it.key] = it }
        codes.forEach { code ->
            Subdivisions.all(code).forEach {
                val p = Province(code, it.code)
                provinceByKey.putIfAbsent(p.key, p)
            }
        }
        meets.filter { it.province.isNotBlank() }.forEach {
            val p = Province(it.country.uppercase(), it.province)
            provinceByKey.putIfAbsent(p.key, p)
        }
        val shownProvinces = provinceByKey.values.filter {
            countries.isEmpty() || it.country.uppercase() in countries || has(it)
        }

        val clubByKey = LinkedHashMap<String, String>()
        clubs.forEach { clubByKey[clubKey(it)] = it }
        meets.filter {
            clubKey(it.organizer).isNotEmpty()
        }.forEach { clubByKey.putIfAbsent(clubKey(it.organizer), it.organizer) }

        return Options(
            countries = codes.sortedWith(compareBy(collator) { countryName(it, lang) }),
            provinces = shownProvinces.sortedWith(
                compareBy(collator) { it.label(lang) },
            ),
            clubs = clubByKey.values.sortedWith(collator),
        )
    }

    companion object {
        /** Two spellings of one club: folded as S-09 folds, then letters and digits only, so `C.A.M.O.` is `CAMO`. */
        internal fun clubKey(club: String): String = SearchFold.fold(club).filter { it.isLetterOrDigit() }

        /**
         * A club typed by the spectator, as kept: its official letters upper-cased, spaces and
         * symbols dropped (` c.a.m.o ` → `CAMO`).
         */
        fun clubLetters(typed: String): String = typed.uppercase().filter { it.isLetterOrDigit() }

        private val json = Json { ignoreUnknownKeys = true }

        fun encode(filter: MeetFilter): String = json.encodeToString(serializer(), filter)

        /** A stored filter, or none: one that no longer decodes costs the filter, never the rest. */
        fun decode(raw: String?): MeetFilter =
            raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: MeetFilter()
    }
}
