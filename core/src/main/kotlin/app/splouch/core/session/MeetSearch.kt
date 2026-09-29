package app.splouch.core.session

import app.splouch.core.schedule.SearchFold
import app.splouch.core.wire.MeetSummary

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
     * What one meet is searched on. The organizer is not on the card but is searched,
     * because a spectator may know a meet by the club running it.
     */
    fun text(m: MeetSummary): String =
        listOf(m.name, m.meetDate, m.location, m.sport, m.organizer).filter { it.isNotEmpty() }.joinToString(" ")

    /** Every word of the query, folded, is somewhere in [text], in any order. A blank query matches. */
    fun matches(text: String, query: String): Boolean {
        val words = words(query)
        if (words.isEmpty()) return true
        val folded = SearchFold.fold(text)
        return words.all { it in folded }
    }

    /** [meets] in the server's order, less the ones [query] hides. */
    fun filter(meets: List<MeetSummary>, query: String): List<MeetSummary> = meets.filter { matches(text(it), query) }

    private fun words(query: String): List<String> = SearchFold.fold(query).split(WHITESPACE).filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
}
