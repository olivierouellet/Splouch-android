package app.splouch.core.board

import app.splouch.core.wire.MeetSettings

/** Which way `settings.lap_direction` counts (app.md `L-23`). */
enum class LapDirection {
    /** The lengths the lane has completed — the console's own number. */
    UP,

    /** What is left of the race: `expected_splits` minus the count. */
    DOWN;

    companion object {
        /**
         * Anything the wire does not recognise — including a server older than the field —
         * counts up, which is the count that needs nothing but the console.
         */
        fun of(raw: String?): LapDirection =
            if (raw?.trim()?.lowercase() == "down") DOWN else UP
    }
}

/**
 * `L-23`'s two settings, resolved from [MeetSettings] once so the board does not carry the
 * whole config down to a cell.
 */
data class LapSettings(val show: Boolean = false, val direction: LapDirection = LapDirection.UP) {
    companion object {
        /** Off: the delta cell has one tenant and the column header is a `Δ` again. */
        val OFF = LapSettings()

        fun from(s: MeetSettings) = LapSettings(s.showLaps, LapDirection.of(s.lapDirection))
    }
}

/**
 * What the delta cell carries while the lap is its tenant (app.md `L-23`).
 *
 * A lap is not a result, so it gets no column of its own and never borrows the place column:
 * `#3` and `3` a length apart are the same glyph. It shares the delta's cell, and the
 * handover is the signal — the colour changes, the header does not.
 */
data class LapCount(
    /** The number as it is drawn — already counted up or down, already clamped. */
    val text: String,
    /**
     * The final stretch. The number stays; the colour moves from the header's accent to the
     * timing colour a stopped chrono has. Not an animation: an earlier version pulsed this
     * and it was removed on purpose.
     */
    val isFinal: Boolean,
)
