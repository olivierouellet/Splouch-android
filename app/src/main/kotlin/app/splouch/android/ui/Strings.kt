package app.splouch.android.ui

import app.splouch.core.strings.StringTable

/**
 * The app's chrome strings (app.md T-05) come from the server through [StringTable].
 * For the keys the server's `[mobile]` section does not yet carry — the filter sheet and
 * the server sheet — a compiled-in English word stands in until they are added
 * upstream (see the open question at the bottom of parity.md). The key names match the
 * iOS app's, so one server change serves both.
 */
fun StringTable.ui(key: String): String {
    val v = mobile(key)
    return if (v != key) v else ENGLISH_FLOOR[key] ?: key
}

private val ENGLISH_FLOOR = mapOf(
    "filter" to "Filter",
    "no_filters" to "No active filters",
    "no_search_results" to "No results",
    "no_matches" to "No swimmers match these filters",
    "swimmer" to "Swimmer",
    "club" to "Club",
    "server" to "Server",
    "add_server" to "Add a server",
    "server_placeholder" to "https://… or http://splouch.local:5000",
    "nearby" to "Nearby",
    "cancel" to "Cancel",
    "done" to "Done",
    "ok" to "OK",
    "retry" to "Retry",
    "remove" to "Remove",
    "language" to "Language",
    "prefs_auto" to "Meet default",
    "prefs_labels" to "Column labels",
    "prefs_short" to "Short",
    "prefs_long" to "Long",
    "meet_gone" to "This meet is no longer available",
    "server_unreachable" to "Cannot reach this server",
    "not_splouch" to "Not a Splouch server",
    "invalid_address" to "Enter a valid address",
    "cleartext_not_local" to "Plain http is only allowed for .local names",
    "open_board" to "Open the board",
    "checking" to "Checking…",
    "needs_android_14" to "Found nearby; needs Android 14 to connect by name",
)
