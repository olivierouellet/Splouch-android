package app.splouch.core.session

import app.splouch.core.wire.MeetConfig

/**
 * The shell's peer sections (app.md `A-01`), as identities rather than indices.
 *
 * Which tabs a meet has is not fixed for the life of the meet: `A-11` takes Results away
 * from a meet with no timing console, the operator can switch consoles mid-meet in either
 * direction, and the config re-fetch that notices is one the app already makes. So page 2
 * is Schedule on one config and does not exist on the next, and a selected tab stored or
 * carried as a number names a different screen than the one the spectator was reading.
 * `A-04` stores a choice, so this is what it stores.
 */
enum class MeetTab {
    SCOREBOARD,
    RESULTS,
    SCHEDULE;

    companion object {
        /** The tab a spectator lands on when the one they were on goes away, or was never stored. */
        val DEFAULT = SCOREBOARD

        /** A persisted value; null for anything absent or unrecognised. */
        fun parse(raw: String?): MeetTab? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

        /**
         * `A-11`: the tabs this meet actually has.
         *
         * Results goes when the meet has no timing console — no `results_snapshot` is ever
         * sent, so `R-01`'s "waiting for results…" would stand from the first heat to the
         * last, which is not a status. The tab goes; its contents are not emptied, greyed
         * or replaced with a notice.
         *
         * Nothing else is conditional. The Scoreboard is exactly as useful — it is what the
         * operator is driving by hand — and the Schedule is the full start list either way.
         */
        fun of(config: MeetConfig): List<MeetTab> =
            if (config.settings.console.timed) entries.toList() else listOf(SCOREBOARD, SCHEDULE)
    }
}
