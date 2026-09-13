package app.splouch.core.session

/** What survives a relaunch, per device: the server, the language and style choices (T-08, T-09), the tab (A-04). */
data class Preferences(
    val server: String? = null,
    /** Servers added by hand (P-13), as origins. */
    val servers: List<String> = emptyList(),
    val lang: String? = null,
    val labelStyle: String? = null,
    val tab: Int? = null,
)

interface PreferencesStore {
    fun load(): Preferences
    fun save(prefs: Preferences)
}

class InMemoryPreferencesStore(private var prefs: Preferences = Preferences()) : PreferencesStore {
    override fun load(): Preferences = prefs
    override fun save(prefs: Preferences) { this.prefs = prefs }
}
