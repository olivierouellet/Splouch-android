package app.splouch.core.session

import app.splouch.core.wire.MeetSummary

/**
 * P-01: the meet list as days, each named once above its meets, so no card repeats its date.
 * [date] is `YYYY-MM-DD`, or `""` for the meets with no date.
 */
data class MeetDay(val date: String, val meets: List<MeetSummary>) {
    companion object {
        /**
         * The server sends meets by date then city (api.md §5.6), which this keeps. Sorted again
         * by date only, stably, so a server from before that order still gives one heading per
         * day; an undated meet goes last.
         */
        fun group(meets: List<MeetSummary>): List<MeetDay> =
            meets.sortedWith(compareBy({ it.meetDate.isEmpty() }, { it.meetDate }))
                .groupBy { it.meetDate }
                .map { (date, list) -> MeetDay(date, list) }
    }
}

/**
 * P-01: the card's second line — city, state/province code, country code (`Montréal`, `QC`,
 * `CA`). A state/province the app does not know is shown as sent. What [filter] narrows to
 * exactly one is left off: with only Canada chosen, no card says `CA`.
 */
fun MeetSummary.place(filter: MeetFilter = MeetFilter()): List<String> = listOf(
    location,
    if (filter.pinsProvince) "" else Subdivisions.lookup(country, province)?.code ?: province,
    if (filter.pinsCountry) "" else country.uppercase(),
).filter { it.isNotBlank() }

/** One country chosen, or provinces of only one: every meet shown is in it. */
val MeetFilter.pinsCountry: Boolean
    get() = countries.size == 1 ||
        (provinces.isNotEmpty() && provinces.map { it.country.uppercase() }.toSet().size == 1)

/** One state/province chosen, however many spellings of it the list held. */
val MeetFilter.pinsProvince: Boolean get() = provinces.map { it.key }.toSet().size == 1
