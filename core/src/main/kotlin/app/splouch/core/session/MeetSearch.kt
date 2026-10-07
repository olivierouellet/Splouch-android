package app.splouch.core.session

import app.splouch.core.schedule.SearchFold
import app.splouch.core.wire.MeetSummary
import java.util.Locale

/**
 * P-17: search over the picker's meet list, entirely on the device — there is no search
 * endpoint, and P-01 already fetched every meet. The fold is S-09's ([SearchFold]), so a
 * meet and a swimmer are found by the same rules.
 */
object MeetSearch {

    /** The field appears at this many meets; under it the picker looks as it did before P-17. */
    const val THRESHOLD = 3

    fun shows(meetCount: Int): Boolean = meetCount >= THRESHOLD

    /**
     * What one meet is searched on. The day, sport and organizer are not on the card but are
     * searched, because a spectator may know a meet by them. The country is searched
     * by its code and by its name in [lang], the reader's language; the province as sent and
     * by its full name in [lang] ([Subdivisions]).
     */
    fun text(m: MeetSummary, lang: String = ""): String = listOf(
        m.name,
        m.meetDate,
        m.location,
        m.sport,
        m.organizer,
        m.province,
        Subdivisions.lookup(m.country, m.province)?.name(lang).orEmpty(),
        m.country,
        countryName(m.country, lang),
    ).filter { it.isNotEmpty() }.joinToString(" ")

    /** Every word of the query, folded, is somewhere in [text], in any order. A blank query matches. */
    fun matches(text: String, query: String): Boolean {
        val words = words(query)
        if (words.isEmpty()) return true
        val folded = SearchFold.fold(text)
        return words.all { it in folded }
    }

    /** [meets] in the server's order — by date, then city — less the ones [query] hides. */
    fun filter(meets: List<MeetSummary>, query: String, lang: String = ""): List<MeetSummary> =
        meets.filter { matches(text(it, lang), query) }

    private fun words(query: String): List<String> = SearchFold.fold(query).split(WHITESPACE).filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
}

/**
 * P-17: an ISO 3166-1 alpha-2 code named in [lang] (`CA` → `Canada`, `Canada`, `Canadá`).
 * `""` for no code; the code itself when the platform has no name for it.
 */
fun countryName(code: String, lang: String): String {
    if (code.length != 2 || !code.all { it in 'A'..'Z' || it in 'a'..'z' }) return code
    val region = try {
        Locale.Builder().setRegion(code).build()
    } catch (_: Exception) {
        return code
    }
    val reader = if (lang.isBlank()) Locale.getDefault() else Locale.forLanguageTag(lang)
    return region.getDisplayCountry(reader).ifBlank { code }
}
