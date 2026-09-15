package app.splouch.core.session

import app.splouch.core.strings.Labels

/** What survives a relaunch, per device: the server, the language and style choices (T-08, T-09), the tab (A-04). */
data class Preferences(
    val server: String? = null,
    /** Servers added by hand (P-13), as origins. */
    val servers: List<String> = emptyList(),
    val lang: String? = null,
    /** T-09's short/long choice — always one of the two, [Labels.DEFAULT] until the user picks. */
    val labelStyle: String = Labels.DEFAULT,
    val tab: Int? = null,
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
