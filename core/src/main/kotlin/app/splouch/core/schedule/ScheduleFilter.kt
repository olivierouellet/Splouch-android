package app.splouch.core.schedule

import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScheduleLane

/** The event and heat the meet is on, as strings (app.md S-05). */
data class HeatRef(val event: String, val heat: String)

data class Filter(val type: SuggestionType, val name: String)

/** The Schedule tab's client-side state (app.md §5.2). Session-only, never persisted (S-20). */
data class ScheduleFilterState(
    val filters: List<Filter> = emptyList(),
    val allHeats: Boolean = false,
    val upcomingOnly: Boolean = false,
) {
    val hasFilters: Boolean get() = filters.isNotEmpty()
    fun contains(f: Filter) = f in filters
    fun add(f: Filter) = if (contains(f)) this else copy(filters = filters + f)
    fun remove(f: Filter) = copy(filters = filters - f)
    fun reset() = ScheduleFilterState()
}

data class VisibleHeat(
    val heat: ScheduleHeat,
    val lanes: List<ScheduleLane>,
    val isCurrent: Boolean,
    /** Position among *visible* cards, so the stripe survives filtering (S-04). */
    val index: Int,
)

enum class EmptyState { NONE, NO_SCHEDULE, NO_MATCHES }

object ScheduleFilter {

    /** S-13, S-14: OR-ed; a swimmer filter matches relay members, not just the display name. */
    fun laneMatches(lane: ScheduleLane, filters: List<Filter>): Boolean {
        if (filters.isEmpty()) return true
        return filters.any { f ->
            when (f.type) {
                SuggestionType.CLUB -> lane.club == f.name
                SuggestionType.SWIMMER -> lane.name == f.name || lane.swimmers.any { it.name == f.name }
            }
        }
    }

    /** S-03: relay member first names joined by `·`, else the lane's name, else a dash. */
    fun displayName(lane: ScheduleLane): String {
        val firsts = lane.swimmers.map { it.display }.filter { it.isNotEmpty() }
        return firsts.joinToString(" · ").ifEmpty { lane.name.ifEmpty { "—" } }
    }

    /** The index of the current heat in the start list, or null when unknown or absent (S-17). */
    fun currentIndex(heats: List<ScheduleHeat>, current: HeatRef?): Int? {
        if (current == null) return null
        return heats.indexOfFirst { it.event == current.event && it.heat == current.heat }.takeIf { it >= 0 }
    }

    fun visible(heats: List<ScheduleHeat>, state: ScheduleFilterState, current: HeatRef?): List<VisibleHeat> {
        // S-17 cuts by position, and changes nothing when the current heat is unknown.
        val cut = if (state.upcomingOnly) currentIndex(heats, current) else null
        val out = ArrayList<VisibleHeat>()
        heats.forEachIndexed { i, h ->
            if (cut != null && i < cut) return@forEachIndexed
            val lanes = if (state.hasFilters) h.lanes.filter { laneMatches(it, state.filters) } else h.lanes
            if (state.hasFilters && !state.allHeats && lanes.isEmpty()) return@forEachIndexed
            val isCurrent = current != null && h.event == current.event && h.heat == current.heat
            out += VisibleHeat(h, lanes, isCurrent, out.size)
        }
        return out
    }

    fun emptyState(heats: List<ScheduleHeat>, visible: List<VisibleHeat>, state: ScheduleFilterState): EmptyState = when {
        heats.isEmpty() -> EmptyState.NO_SCHEDULE
        visible.isEmpty() && state.hasFilters -> EmptyState.NO_MATCHES
        else -> EmptyState.NONE
    }

    /**
     * S-21: after a re-fetch, keep the filters whose names still exist in the new list and
     * drop the rest silently.
     */
    fun retain(state: ScheduleFilterState, heats: List<ScheduleHeat>): ScheduleFilterState {
        if (!state.hasFilters) return state
        val lanes = heats.asSequence().flatMap { it.lanes.asSequence() }
        val clubs = lanes.map { it.club }.toSet()
        val swimmers = lanes.flatMap { l -> sequenceOf(l.name) + l.swimmers.asSequence().map { it.name } }.toSet()
        return state.copy(filters = state.filters.filter {
            when (it.type) {
                SuggestionType.CLUB -> it.name in clubs
                SuggestionType.SWIMMER -> it.name in swimmers
            }
        })
    }
}
