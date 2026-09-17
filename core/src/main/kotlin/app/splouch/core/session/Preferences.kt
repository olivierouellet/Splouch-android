package app.splouch.core.session

import app.splouch.core.strings.Labels

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
    AUTO;

    companion object {
        /** A stored value, or [DARK] for anything absent or unrecognised. */
        fun parse(raw: String?): Appearance = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

/** What survives a relaunch, per device: the server, the language and style choices (T-08, T-09), the appearance (P-15), the tab (A-04). */
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
) {
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
    override fun save(prefs: Preferences) { this.prefs = prefs }
}
