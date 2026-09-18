package app.splouch.core.wire

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ── GET /server (api.md §5.10) ────────────────────────────────────────────────

enum class ServerKind(val wire: String) {
    PI("pi"), CLOUD("cloud");

    companion object {
        fun fromWire(s: String?): ServerKind? = entries.firstOrNull { it.wire == s?.trim()?.lowercase() }
    }
}

/** The contract versions a server or this app implements: bare tags, compared for equality. */
data class ContractVersions(val api: String, val app: String) {
    companion object {
        fun fromJson(e: JsonElement?): ContractVersions {
            val o = e.asObjectOrNull()
            return ContractVersions(
                api = o?.get("api").asStringOrNull()?.trim() ?: "",
                app = o?.get("app").asStringOrNull()?.trim() ?: "",
            )
        }
    }
}

data class ServerInfo(val kind: ServerKind, val name: String, val contract: ContractVersions) {
    companion object {
        /**
         * Null when the body is not a Splouch handshake. An unknown `kind` counts as one:
         * the contract names two, and the app cannot shape a session around a third.
         */
        fun fromJson(e: JsonElement?): ServerInfo? {
            val o = e.asObjectOrNull() ?: return null
            val kind = ServerKind.fromWire(o["kind"].asStringOrNull()) ?: return null
            return ServerInfo(
                kind = kind,
                name = o["name"].asStringOrNull()?.trim().orEmpty(),
                contract = ContractVersions.fromJson(o["contract"]),
            )
        }
    }
}

// ── GET /servers (api.md §5.11) ───────────────────────────────────────────────

data class ServerEntry(val name: String, val url: String, val kind: ServerKind?) {
    companion object {
        fun listFromJson(e: JsonElement?): List<ServerEntry> =
            e.asObjectOrNull()?.get("servers").asArrayOrNull()?.mapNotNull { item ->
                val o = item.asObjectOrNull() ?: return@mapNotNull null
                val url = o["url"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                ServerEntry(
                    name = o["name"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: url,
                    url = url,
                    kind = ServerKind.fromWire(o["kind"].asStringOrNull()),
                )
            } ?: emptyList()
    }
}

// ── GET /meets (api.md §5.6) ──────────────────────────────────────────────────

data class MeetSummary(
    val id: String,
    val name: String,
    val location: String,
    val sport: String,
    val organizer: String,
    val meetDate: String,
    val offline: Boolean,
    val hasPickerImage: Boolean,
) {
    companion object {
        fun fromJson(e: JsonElement?): MeetSummary? {
            val o = e.asObjectOrNull() ?: return null
            val id = o["id"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return MeetSummary(
                id = id,
                name = o["name"].asStringOrNull().orEmpty(),
                location = o["location"].asStringOrNull().orEmpty(),
                sport = o["sport"].asStringOrNull().orEmpty(),
                organizer = o["organizer"].asStringOrNull().orEmpty(),
                meetDate = o["meet_date"].asStringOrNull().orEmpty(),
                offline = o["offline"].asBoolOrNull() ?: false,
                hasPickerImage = o["has_picker_image"].asBoolOrNull() ?: false,
            )
        }

        fun listFromJson(e: JsonElement?): List<MeetSummary> =
            e.asObjectOrNull()?.get("meets").asArrayOrNull()?.mapNotNull { fromJson(it) } ?: emptyList()
    }
}

// ── GET /picker/config (api.md §5.7) ──────────────────────────────────────────

data class PickerConfig(
    val title: String,
    val windowTitle: String,
    val hasLogo: Boolean,
    val logoAbove: Boolean,
    val lang: String,
    val analyticsEnabled: Boolean,
    val strings: Map<String, String>,
) {
    companion object {
        fun fromJson(e: JsonElement?): PickerConfig? {
            val o = e.asObjectOrNull() ?: return null
            return PickerConfig(
                title = o["title"].asStringOrNull() ?: "Splouch",
                windowTitle = o["window_title"].asStringOrNull() ?: "Splouch",
                hasLogo = o["has_logo"].asBoolOrNull() ?: false,
                logoAbove = o["logo_above"].asBoolOrNull() ?: false,
                lang = o["lang"].asStringOrNull()?.trim().orEmpty(),
                analyticsEnabled = o["analytics_enabled"].asBoolOrNull() ?: false,
                strings = o["strings"].asObjectOrNull().stringMap(),
            )
        }
    }
}

// ── Meet settings (api.md §5.4 `settings`; the Pi's GET /config carries the same keys flat) ──

/**
 * Which console is driving this meet (api.md §5.4), from `settings.console` on the cloud
 * and the same block at the top level of the Pi's `GET /config` (§6).
 *
 * [timed] is the half the app acts on: false means no time and no place will ever arrive,
 * because the operator is driving the boards by hand from `/manual`, and that is what
 * takes the Results tab away (`A-11`). The server reads it off the decoder, so a console
 * added as a local plugin and driven by hand answers it correctly — which is why nothing
 * here branches on [key], and why a client that matched `key == "manual"` would call that
 * plugin timed.
 *
 * [key] is diagnostic only: *which console did this meet run on*, for a support question.
 * Its human label does not travel — it exists in English only.
 */
data class ConsoleInfo(val key: String = "", val timed: Boolean = true) {
    companion object {
        /**
         * What a server too old to send `console` means, and what a malformed value reads
         * as: there is a console, and it times. The absent field has always meant that, so
         * the default is to show the tab and never to take one away on a parse failure.
         */
        val TIMED = ConsoleInfo()

        fun fromJson(e: JsonElement?): ConsoleInfo {
            val o = e.asObjectOrNull() ?: return TIMED
            return ConsoleInfo(
                key = o["key"].asStringOrNull()?.trim().orEmpty(),
                timed = o["timed"].asBoolOrNull() ?: true,
            )
        }
    }
}

data class MeetSettings(
    val numLanes: Int,
    val showName: Boolean = true,
    val showClub: Boolean = true,
    val showDelta: Boolean = true,
    val showPosition: Boolean = true,
    val showPodium: Boolean = true,
    val showLaneHeader: Boolean = true,
    val showNameHeader: Boolean = true,
    val showClubHeader: Boolean = true,
    val showTimeHeader: Boolean = true,
    val showDeltaHeader: Boolean = true,
    val showPositionHeader: Boolean = true,
    /**
     * L-23: whether the delta cell carries a lane's lengths while it is still swimming.
     *
     * The one display flag that is **off** by default rather than on — not every console's
     * count is exact (api.md §5.1), so the operator turns it on for a venue where it is.
     */
    val showLaps: Boolean = false,
    /** `"up"` or `"down"` as the operator set it, raw; anything else counts up ([app.splouch.core.board.LapDirection]). */
    val lapDirection: String? = null,
    val themeColors: Map<String, String> = emptyMap(),
    val themeFonts: Map<String, String> = emptyMap(),
    /** The meet's language (app.md T-06); null when the server did not say. */
    val locale: String? = null,
    /** Labels as the operator resolved them: the default before any user preference (T-04). */
    val labels: Map<String, String> = emptyMap(),
    /** `"short"` or `"long"`, as the operator set it; null when absent (the Pi's /config). T-09's control starts from long either way. */
    val labelStyle: String? = null,
    /** A-11: which console drives this meet, and whether it times at all. [ConsoleInfo.TIMED] when the server did not say. */
    val console: ConsoleInfo = ConsoleInfo.TIMED,
) {
    companion object {
        const val DEFAULT_NUM_LANES = 8
        const val MAX_LANES = 12

        fun fromJson(e: JsonElement?, defaultLanes: Int = DEFAULT_NUM_LANES): MeetSettings {
            val o = e.asObjectOrNull()
            fun flag(key: String) = o?.get(key).asBoolOrNull() ?: true
            return MeetSettings(
                numLanes = (o?.get("num_lanes").asIntOrNull() ?: defaultLanes).coerceIn(1, MAX_LANES),
                showName = flag("show_name"),
                showClub = flag("show_club"),
                showDelta = flag("show_delta"),
                showPosition = flag("show_position"),
                showPodium = flag("show_podium"),
                showLaneHeader = flag("show_lane_header"),
                showNameHeader = flag("show_name_header"),
                showClubHeader = flag("show_club_header"),
                showTimeHeader = flag("show_time_header"),
                showDeltaHeader = flag("show_delta_header"),
                showPositionHeader = flag("show_position_header"),
                // Not `flag(...)`: this is the one that ships off, and a server too old to
                // send it must not turn the column into lengths on its own.
                showLaps = o?.get("show_laps").asBoolOrNull() ?: false,
                lapDirection = o?.get("lap_direction").asStringOrNull()?.trim()?.lowercase()
                    ?.takeIf { it.isNotEmpty() },
                themeColors = o?.get("theme_colors").asObjectOrNull().stringMap(),
                themeFonts = o?.get("theme_fonts").asObjectOrNull().stringMap(),
                locale = o?.get("locale").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
                labels = o?.get("labels").asObjectOrNull().stringMap(),
                labelStyle = o?.get("label_style").asStringOrNull()?.trim()?.lowercase()
                    ?.takeIf { it == "short" || it == "long" },
                console = ConsoleInfo.fromJson(o?.get("console")),
            )
        }
    }
}

/**
 * A meet's config, from `GET /meet/{id}/config` on the cloud or `GET /config` on the Pi
 * (app.md §0.2). The two differ only in where the name lives and whether `live` is known.
 */
data class MeetConfig(
    val name: String,
    val location: String = "",
    val sport: String = "",
    val appWindowTitle: String = "",
    val meetDate: String = "",
    /** The cloud says whether a relay is attached; the Pi does not say, the socket does. */
    val live: Boolean? = null,
    val settings: MeetSettings,
) {
    /** A-08's rule for the shell title: `app_window_title`, then `name`, then `Splouch`. */
    val title: String get() = appWindowTitle.trim().ifEmpty { name.trim().ifEmpty { "Splouch" } }

    companion object {
        fun fromCloudJson(e: JsonElement?): MeetConfig? {
            val o = e.asObjectOrNull() ?: return null
            return MeetConfig(
                name = o["name"].asStringOrNull().orEmpty(),
                location = o["location"].asStringOrNull().orEmpty(),
                sport = o["sport"].asStringOrNull().orEmpty(),
                appWindowTitle = o["app_window_title"].asStringOrNull().orEmpty(),
                meetDate = o["meet_date"].asStringOrNull().orEmpty(),
                live = o["live"].asBoolOrNull(),
                settings = MeetSettings.fromJson(o["settings"]),
            )
        }

        fun fromPiJson(e: JsonElement?): MeetConfig? {
            val o = e.asObjectOrNull() ?: return null
            return MeetConfig(
                name = o["meet_title"].asStringOrNull().orEmpty(),
                live = null,
                settings = MeetSettings.fromJson(o, defaultLanes = 6),
            )
        }
    }
}

// ── event_name_parts (api.md §5.1) ────────────────────────────────────────────

data class EventNameParts(
    val raw: String = "",
    val dist: String = "",
    val stroke: String = "",
    val relay: Boolean = false,
    val gender: String = "",
    val age: String = "",
    val ageKey: String = "",
) {
    companion object {
        fun fromJson(e: JsonElement?): EventNameParts? {
            val o = e.asObjectOrNull() ?: return null
            fun s(k: String) = o[k].asStringOrNull()?.trim().orEmpty()
            return EventNameParts(
                raw = s("raw"), dist = s("dist"), stroke = s("stroke"),
                relay = o["relay"].asBoolOrNull() ?: false,
                gender = s("gender"), age = s("age"), ageKey = s("age_key"),
            )
        }
    }
}

// ── update_scoreboard (api.md §5.1): a partial, flat dict ─────────────────────

/**
 * One `update_scoreboard` frame — only the keys that changed. The board merges it
 * (app.md L-10); nothing here is state.
 */
class ScoreboardFrame(val fields: Map<String, JsonElement>) {

    fun has(key: String): Boolean = key in fields
    fun string(key: String): String? = fields[key].asStringOrNull()

    /** Event and heat, as strings, trimmed — the only form they are ever compared in. */
    val currentEvent: String? get() = string("current_event")?.trim()
    val currentHeat: String? get() = string("current_heat")?.trim()
    val eventName: String? get() = string("event_name")
    val hasEventNameParts: Boolean get() = has("event_name_parts")
    val eventNameParts: EventNameParts? get() = EventNameParts.fromJson(fields["event_name_parts"])
    val runningTime: String? get() = string("running_time")

    /**
     * L-23's two venue numbers. They describe the pool, not the console, and arrive together
     * on every heat change. Null when the frame did not carry them — the board keeps what it
     * has; a value that does not decode is the same nothing, and so is a negative one.
     */
    val expectedSplits: Int? get() = fields["expected_splits"].asIntOrNull()?.coerceAtLeast(0)

    /** Floored at 1: a `split_step` of 0 would fire the final-stretch test a length early. */
    val splitStep: Int? get() = fields["split_step"].asIntOrNull()?.coerceAtLeast(1)

    fun laneName(i: Int): String? = string("lane_name$i")
    fun laneNameAlt(i: Int): String? = string("lane_name_alt$i")
    fun laneClub(i: Int): String? = string("lane_club$i")
    fun laneTime(i: Int): String? = string("lane_time$i")
    fun lanePlace(i: Int): String? = string("lane_place$i")
    fun laneRunning(i: Int): Boolean? = fields["lane_running$i"].asBoolOrNull()
    fun hasLaneDeltaSeconds(i: Int): Boolean = has("lane_delta_seconds$i")
    fun laneDeltaSeconds(i: Int): Double? = fields["lane_delta_seconds$i"].asDoubleOrNull()
    fun hasLaneDeltaBetter(i: Int): Boolean = has("lane_delta_better$i")
    fun laneDeltaBetter(i: Int): Boolean? = fields["lane_delta_better$i"].asBoolOrNull()

    /**
     * L-23: lengths lane `i` has completed. Null when the frame did not carry the key; a key
     * that is there but does not decode reads as 0, which is what the server sends at the top
     * of every heat anyway.
     */
    fun laneSplits(i: Int): Int? =
        if (has("lane_splits$i")) (fields["lane_splits$i"].asIntOrNull() ?: 0).coerceAtLeast(0) else null

    /** Every `lane_running<i>` on this frame, lane → flag. */
    fun runningEdges(): Map<Int, Boolean> = fields.entries.mapNotNull { (k, v) ->
        RUNNING_KEY.matchEntire(k)?.groupValues?.get(1)?.toIntOrNull()?.let { i -> v.asBoolOrNull()?.let { i to it } }
    }.toMap()

    /** True when any `lane_name<i>` (not `_alt`) is on the frame — the L-17 re-fit gate. */
    fun carriesNames(): Boolean = fields.keys.any { NAME_KEY.matches(it) }

    companion object {
        private val RUNNING_KEY = Regex("""^lane_running(\d+)$""")
        private val NAME_KEY = Regex("""^lane_name(\d+)$""")

        fun fromJson(e: JsonElement?): ScoreboardFrame? = e.asObjectOrNull()?.let { ScoreboardFrame(it) }
    }
}

data class MeetLive(val live: Boolean) {
    companion object {
        fun fromJson(e: JsonElement?): MeetLive = MeetLive(e.asObjectOrNull()?.get("live").asBoolOrNull() ?: false)
    }
}

// ── results_snapshot (api.md §5.2) ────────────────────────────────────────────

data class ResultLane(
    val channel: Int?,
    val place: String,
    val time: String,
    val name: String,
    val club: String,
    val alt: String,
    val deltaSeconds: Double?,
    val deltaBetter: Boolean?,
) {
    companion object {
        fun fromJson(e: JsonElement?): ResultLane? {
            val o = e.asObjectOrNull() ?: return null
            return ResultLane(
                channel = o["channel"].asIntOrNull(),
                place = o["place"].asStringOrNull()?.trim().orEmpty(),
                time = o["time"].asStringOrNull()?.trim().orEmpty(),
                name = o["name"].asStringOrNull().orEmpty(),
                club = o["club"].asStringOrNull().orEmpty(),
                alt = o["alt"].asStringOrNull().orEmpty(),
                deltaSeconds = o["delta_seconds"].asDoubleOrNull(),
                deltaBetter = o["delta_better"].asBoolOrNull(),
            )
        }
    }
}

data class ResultsSnapshot(
    val event: String,
    val heat: String,
    val eventName: String,
    val eventNameParts: EventNameParts?,
    /** `"lane"`, `"place"`, or null when the relay predates the field — read as lane (R-05). */
    val sort: String?,
    val lanes: List<ResultLane>,
) {
    val isLaneSort: Boolean get() = sort == null || sort == "lane"

    companion object {
        fun fromJson(e: JsonElement?): ResultsSnapshot? {
            val o = e.asObjectOrNull() ?: return null
            return ResultsSnapshot(
                event = o["event"].asStringOrNull()?.trim().orEmpty(),
                heat = o["heat"].asStringOrNull()?.trim().orEmpty(),
                eventName = o["event_name"].asStringOrNull().orEmpty(),
                eventNameParts = EventNameParts.fromJson(o["event_name_parts"]),
                sort = o["sort"].asStringOrNull()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                lanes = o["lanes"].asArrayOrNull()?.mapNotNull { ResultLane.fromJson(it) } ?: emptyList(),
            )
        }
    }
}

// ── GET /meet/{id}/schedule, GET /schedule.json (api.md §5.8) ─────────────────

data class ScheduleSwimmer(val name: String, val first: String) {
    /** S-03: the first name, falling back to the full name. */
    val display: String get() = first.ifBlank { name }

    companion object {
        fun fromJson(e: JsonElement?): ScheduleSwimmer? {
            val o = e.asObjectOrNull() ?: return null
            return ScheduleSwimmer(
                name = o["name"].asStringOrNull()?.trim().orEmpty(),
                first = o["first"].asStringOrNull()?.trim().orEmpty(),
            )
        }
    }
}

data class ScheduleLane(
    val lane: Int?,
    val name: String,
    val club: String,
    val seedTime: String,
    val swimmers: List<ScheduleSwimmer>,
) {
    companion object {
        fun fromJson(e: JsonElement?): ScheduleLane? {
            val o = e.asObjectOrNull() ?: return null
            return ScheduleLane(
                lane = o["lane"].asIntOrNull(),
                name = o["name"].asStringOrNull()?.trim().orEmpty(),
                club = o["club"].asStringOrNull()?.trim().orEmpty(),
                seedTime = o["seed_time"].asStringOrNull()?.trim().orEmpty(),
                swimmers = o["swimmers"].asArrayOrNull()?.mapNotNull { ScheduleSwimmer.fromJson(it) } ?: emptyList(),
            )
        }
    }
}

data class ScheduleHeat(
    /** Integers on the wire, strings here: they are only ever compared to `current_event` (S-05). */
    val event: String,
    val heat: String,
    val eventName: String,
    val eventNameParts: EventNameParts?,
    val time: String,
    val lanes: List<ScheduleLane>,
) {
    companion object {
        fun fromJson(e: JsonElement?): ScheduleHeat? {
            val o = e.asObjectOrNull() ?: return null
            return ScheduleHeat(
                event = o["event"].asStringOrNull()?.trim().orEmpty(),
                heat = o["heat"].asStringOrNull()?.trim().orEmpty(),
                eventName = o["event_name"].asStringOrNull().orEmpty(),
                eventNameParts = EventNameParts.fromJson(o["event_name_parts"]),
                time = o["time"].asStringOrNull()?.trim().orEmpty(),
                lanes = o["lanes"].asArrayOrNull()?.mapNotNull { ScheduleLane.fromJson(it) } ?: emptyList(),
            )
        }

        /** Null when the body is not a schedule; an empty list when it is one with no heats yet. */
        fun listFromJson(e: JsonElement?): List<ScheduleHeat>? {
            val o = e.asObjectOrNull() ?: return null
            val heats = o["heats"].asArrayOrNull() ?: return null
            return heats.mapNotNull { fromJson(it) }
        }
    }
}

// ── GET /locales, GET /i18n/{lang} (api.md §5.9) ─────────────────────────────

data class LocaleEntry(val code: String, val name: String) {
    companion object {
        fun listFromJson(e: JsonElement?): List<LocaleEntry> =
            e.asArrayOrNull()?.mapNotNull { item ->
                val o = item.asObjectOrNull() ?: return@mapNotNull null
                val code = o["code"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                LocaleEntry(code, o["name"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: code)
            } ?: emptyList()
    }
}

/**
 * One language's strings, the body of `GET /i18n/{lang}`. The compiled-in snapshot and
 * the disk cache hold this same shape (app.md T-10), so there is one decoder.
 */
data class I18nBundle(
    val lang: String,
    val mobile: Map<String, String>,
    val display: Map<String, String>,
    /** `short` and `long` tables, each key → word. */
    val labels: Map<String, Map<String, String>>,
    val eventName: Map<String, String>,
) {
    companion object {
        fun fromJson(e: JsonElement?): I18nBundle? {
            val o = e.asObjectOrNull() ?: return null
            val lang = o["lang"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return I18nBundle(
                lang = lang,
                mobile = o["mobile"].asObjectOrNull().stringMap(),
                display = o["display"].asObjectOrNull().stringMap(),
                labels = o["labels"].asObjectOrNull()?.entries
                    ?.mapNotNull { (style, words) -> words.asObjectOrNull()?.let { style to it.stringMap() } }
                    ?.toMap() ?: emptyMap(),
                eventName = o["event_name"].asObjectOrNull().stringMap(),
            )
        }

        fun fromText(text: String): I18nBundle? = fromJson(parseJsonOrNull(text))
    }
}
