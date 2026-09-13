package app.splouch.core.theme

import app.splouch.core.wire.MeetSettings

/**
 * The meet's palette and font roles (app.md T-01..T-03), with the documented defaults
 * for any key the server left out (T-07). Colours stay hex strings here; the app parses
 * them. Podium tints are deliberately absent: they are not in the contract.
 */
data class Theme(val colors: Map<String, String>, val fonts: Map<String, String>) {

    fun color(key: String): String = colors[key]?.takeIf { it.isNotBlank() } ?: DEFAULT_COLORS[key] ?: "#ffffff"
    fun font(role: String): String = fonts[role]?.takeIf { it.isNotBlank() } ?: DEFAULT_FONTS[role] ?: "Overpass Mono"

    companion object {
        val DEFAULT_COLORS: Map<String, String> = mapOf(
            "bg" to "#0d0d0d", "header_bg" to "#1a1a1a", "header_border" to "#2e2e2e",
            "header_label" to "#ffffff", "header_value" to "#e0e0e0",
            "th_text" to "#666666", "th_bg" to "#1a1a1a",
            "row_odd" to "#141414", "row_even" to "#202020", "row_text" to "#e0e0e0",
            "time" to "#FFD700", "delta_better" to "#4CAF50", "delta_worse" to "#808080",
            "schedule_event" to "#3b9eff", "schedule_time" to "#FFD700",
            "schedule_name" to "#e0e0e0", "schedule_club" to "#666666",
        )
        val DEFAULT_FONTS: Map<String, String> = mapOf(
            "family" to "Overpass Mono", "digits" to "DSEG7Classic", "timing" to "Overpass Mono",
        )

        /** The six bundled faces (app.md T-03); anything else falls back to a system monospace. */
        val BUNDLED_FONTS = listOf(
            "Overpass Mono", "DSEG7Classic", "DSEG14Classic", "Share Tech Mono", "Orbitron", "Roboto Mono",
        )

        val DEFAULT = Theme(emptyMap(), emptyMap())

        fun from(settings: MeetSettings) = Theme(settings.themeColors, settings.themeFonts)

        /** `#rgb`, `#rrggbb` or `#aarrggbb` → ARGB int; null for anything else. */
        fun parseArgb(hex: String): Long? {
            val s = hex.trim().removePrefix("#")
            val expanded = when (s.length) {
                3 -> s.map { "$it$it" }.joinToString("")
                6, 8 -> s
                else -> return null
            }
            val v = expanded.toLongOrNull(16) ?: return null
            return if (expanded.length == 6) 0xFF000000L or v else v
        }
    }
}
