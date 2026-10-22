package app.splouch.core.session

import app.splouch.core.strings.Labels
import app.splouch.core.wire.MeetSettings

/**
 * P-15: light or dark, the app's own and not the meet's.
 *
 * The choice holds **everywhere** — picker, board and the chrome over both. A meet used to
 * decide inside itself from `settings.theme_colors`, which meant a reader who chose Light
 * got it until they opened a meet, which is where they were going. So there is no
 * "follow the meet" case here, and the board reads this back rather than voting.
 *
 * [DARK] rather than [AUTO] by default: the app has been pinned dark since the web page it
 * came from, and a spectator who never opens this menu should see the app they saw
 * yesterday. That holds for preferences stored before the key existed, too.
 */
enum class Appearance {
    DARK,
    LIGHT,

    /** Follow the device, which is what lets it change with the time of day. */
    AUTO,

    ;

    companion object {
        /** A stored value, or [DARK] for anything absent or unrecognised. */
        fun parse(raw: String?): Appearance = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

/**
 * P-23: the reader's results order. It outranks the snapshot's `sort`, the order the operator set,
 * on every meet's Results tab; the Scoreboard is always by lane.
 */
enum class ResultsOrder {
    LANE,
    PLACE,

    ;

    companion object {
        /** A stored value, or [PLACE] for anything absent or unrecognised — the withdrawn `POOL` too. */
        fun parse(raw: String?): ResultsOrder = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PLACE
    }
}

/**
 * What survives a relaunch, per device: the server, the language and style choices (T-08, T-09),
 * the appearance (P-15), the tab (A-04), whether the introduction has been seen (P-20), the
 * picker's filter (P-21), the results order (P-23) and which meets run their lanes the other way
 * (L-26). The schedule's filters are deliberately not here (S-20).
 */
data class Preferences(
    val server: String? = null,
    /** Servers added by hand (P-13), as origins. */
    val servers: List<String> = emptyList(),
    val lang: String? = null,
    /** T-09's short/long choice — always one of the two, [Labels.DEFAULT] until the user picks. */
    val labelStyle: String = Labels.DEFAULT,
    /** P-15: the app's own light/dark, [Appearance.DARK] until the user says otherwise. */
    val appearance: Appearance = Appearance.DARK,
    /**
     * A-04: which tab, by identity — never its index. A meet's tab row can lose Results
     * mid-session (`A-11`), so a stored number would hand a returning spectator whichever
     * screen now sits at that position. Null until a tab has settled.
     */
    val tab: MeetTab? = null,
    /** P-20: finished or skipped once on this install. Never set by a launch that had no server to show it with. */
    val introSeen: Boolean = false,
    /** P-21: one for the app, whichever server is in use. */
    val meetFilter: MeetFilter = MeetFilter(),
    /** P-23: [ResultsOrder.PLACE] until the reader picks. */
    val resultsOrder: ResultsOrder = ResultsOrder.PLACE,
    /**
     * L-26: the meets this reader set descending, and those set ascending, by
     * [MeetContext.laneKey]. A key is in one at most; a meet in neither follows the pool.
     */
    val descendingLanes: Set<String> = emptySet(),
    val ascendingLanes: Set<String> = emptySet(),
) {
    /** L-26: this meet's lanes run bottom to top — the reader's choice, else the pool's `lane_direction`. */
    fun lanesDescending(laneKey: String, settings: MeetSettings): Boolean = when (laneKey) {
        in descendingLanes -> true
        in ascendingLanes -> false
        else -> settings.laneDirection == "down"
    }

    /**
     * What the board actually renders (T-09). The control is withdrawn from the UI, so
     * this answers [Labels.LONG] whatever [labelStyle] holds.
     *
     * Withdrawn, not deleted: it reads *over* the stored value rather than rewriting it,
     * so a user who had chosen short still has that choice on disk and gets it back the
     * day the control returns. app.md T-09 is explicit that a release may take the
     * control away and may not discard the preference behind it.
     */
    val effectiveLabelStyle: String get() = Labels.LONG
}

interface PreferencesStore {
    fun load(): Preferences
    fun save(prefs: Preferences)
}

class InMemoryPreferencesStore(private var prefs: Preferences = Preferences()) : PreferencesStore {
    override fun load(): Preferences = prefs
    override fun save(prefs: Preferences) {
        this.prefs = prefs
    }
}
