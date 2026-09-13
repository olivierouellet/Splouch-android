package app.splouch.core.schedule

import app.splouch.core.wire.ScheduleHeat

enum class SuggestionType { SWIMMER, CLUB }

/** One typeahead row (app.md `S-10`): its kind, the name to filter on, and the club to show. */
data class Suggestion(val type: SuggestionType, val name: String, val club: String)

/**
 * `S-09`: typeahead over the swimmers and clubs of the start list, built entirely on the
 * client from the `S-01` payload — `lane.name`, `lane.club`, `lane.swimmers[].name` are the
 * only three fields it ever needed, and the app already holds them. No request, so no
 * round-trip per keystroke and no window where the server suggests a swimmer the local list
 * does not have yet.
 *
 * Rebuilt whenever the schedule is re-fetched (`S-21`); [ScheduleFilter.retain] is what keeps
 * the user's active filters across that rebuild.
 */
data class SuggestionIndex(private val entries: List<Entry>) {

    /** A suggestion with its folded key precomputed — folding on every comparison would be the lag. */
    data class Entry(val suggestion: Suggestion, val key: String)

    /**
     * Substring of the folded query against the folded key, nothing else. Swimmers first,
     * then clubs, each already in name order, capped at [MAX] rows total.
     */
    fun search(query: String, limit: Int = MAX): List<Suggestion> {
        if (query.isBlank()) return emptyList()
        val q = SearchFold.fold(query)
        // A query of nothing but non-ASCII folds away; it must not match every entry.
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Suggestion>(minOf(limit, entries.size))
        for (e in entries) {
            if (out.size >= limit) break
            if (e.key.contains(q)) out += e.suggestion
        }
        return out
    }

    companion object {
        const val MAX = 20

        val EMPTY = SuggestionIndex(emptyList())

        fun from(heats: List<ScheduleHeat>?): SuggestionIndex {
            if (heats.isNullOrEmpty()) return EMPTY
            // Name → club, in schedule order: a later lane's club wins for the same name.
            val names = LinkedHashMap<String, String>()
            val clubs = LinkedHashSet<String>()
            for (h in heats) for (l in h.lanes) {
                // The lane's own name too, relay teams included: a spectator may know the
                // team and not one swimmer on it.
                if (l.name.isNotEmpty()) names[l.name] = l.club
                for (s in l.swimmers) if (s.name.isNotEmpty()) names[s.name] = l.club
                if (l.club.isNotEmpty()) clubs += l.club
            }
            val swimmers = names.map { (name, club) -> entry(SuggestionType.SWIMMER, name, club) }
            val byClub = clubs.map { entry(SuggestionType.CLUB, it, "") }
            // Sorted once, here: a filtered pass then yields them in order for free.
            return SuggestionIndex(swimmers.sortedWith(ORDER) + byClub.sortedWith(ORDER))
        }

        private fun entry(type: SuggestionType, name: String, club: String) =
            Entry(Suggestion(type, name, club), SearchFold.fold(name))

        /** By the folded key, so accents sort where a reader expects; the raw name breaks ties. */
        private val ORDER = compareBy<Entry>({ it.key }, { it.suggestion.name })
    }
}
