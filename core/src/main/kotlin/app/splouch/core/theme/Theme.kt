package app.splouch.core.theme

import app.splouch.core.wire.MeetSettings

/**
 * The meet's font roles (app.md T-03), and the palette in two of them: one dark, one light.
 *
 * **This app does not render `settings.theme_colors`.** `T-01` and `T-02` have the board
 * take its palette from the meet's config, and that is still what the web board and the Pi
 * display do — but on a phone the reader chooses (`P-15`), and a preference that the next
 * meet could overrule is not a preference. The field is still decoded and still reaches
 * [colors]; nothing draws from it. The cost is deliberate and worth naming: an operator who
 * themed a meet in club colours sees them everywhere except here. See `parity.md` T-01.
 *
 * The two palettes are the server's own — `DEFAULT_THEME_COLORS` in `server/state.py` and
 * `server/themes/white.toml` — so a board looks like Splouch either way round rather than
 * like something this app invented. Colours stay hex strings here; the app parses them.
 * Podium tints are deliberately absent from both: they are not in the contract.
 *
 * The **faces** are still the meet's. `T-03` is untouched by any of this.
 */
data class Theme(val colors: Map<String, String>, val fonts: Map<String, String>) {

    fun color(key: String): String = colors[key]?.takeIf { it.isNotBlank() } ?: DEFAULT_COLORS[key] ?: "#ffffff"
    fun font(role: String): String = fonts[role]?.takeIf { it.isNotBlank() } ?: DEFAULT_FONTS[role] ?: "Overpass Mono"

    companion object {
        /**
         * The dark board: `DEFAULT_THEME_COLORS` in `server/state.py`.
         *
         * One key differs on purpose. The server has `header_label` at `#3b9eff`, the same
         * blue as `schedule_event`; this app and its iOS twin have carried `#ffffff` since
         * before the palette was pinned, and every dark board anyone has seen on a phone
         * has had white EVENT and HEAT words over it. Changing it now would restyle the
         * board to fix a number nobody is reading.
         */
        val DEFAULT_COLORS: Map<String, String> = mapOf(
            "bg" to "#0d0d0d", "header_bg" to "#1a1a1a", "header_border" to "#2e2e2e",
            "header_label" to "#ffffff", "header_value" to "#e0e0e0",
            "th_text" to "#666666", "th_bg" to "#1a1a1a",
            "row_odd" to "#141414", "row_even" to "#202020", "row_text" to "#e0e0e0",
            "time" to "#FFD700", "delta_better" to "#4CAF50", "delta_worse" to "#808080",
            "schedule_event" to "#3b9eff", "schedule_time" to "#FFD700",
            "schedule_name" to "#e0e0e0", "schedule_club" to "#666666",
        )

        /**
         * The light board: `server/themes/white.toml`, minus `connection_lost` and
         * `connection_lost_text` — those two are the Qt display's, the one client that can
         * tell the console has stopped talking to it, and nothing here draws them.
         *
         * Every key [DEFAULT_COLORS] has, this has too. A missing one would fall through to
         * the dark default and paint a dark row into a light board; `StringsThemeScheduleTests`
         * fails the build if the two ever drift apart.
         */
        val LIGHT_COLORS: Map<String, String> = mapOf(
            "bg" to "#f8f8f8", "header_bg" to "#ffffff", "header_border" to "#dddddd",
            "header_label" to "#333333", "header_value" to "#111111",
            "th_text" to "#888888", "th_bg" to "#f0f0f0",
            "row_odd" to "#f5f5f5", "row_even" to "#ffffff", "row_text" to "#111111",
            "time" to "#0055aa", "delta_better" to "#2e7d32", "delta_worse" to "#757575",
            "schedule_event" to "#0055cc", "schedule_time" to "#0055aa",
            "schedule_name" to "#111111", "schedule_club" to "#888888",
        )

        /** The palette a reader who chose dark, or light, is shown (`P-15`). */
        fun palette(dark: Boolean): Map<String, String> = if (dark) DEFAULT_COLORS else LIGHT_COLORS

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
