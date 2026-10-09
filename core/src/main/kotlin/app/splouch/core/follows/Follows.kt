package app.splouch.core.follows

import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.asArrayOrNull
import app.splouch.core.wire.asBoolOrNull
import app.splouch.core.wire.asIntOrNull
import app.splouch.core.wire.asObjectOrNull
import app.splouch.core.wire.asStringOrNull
import app.splouch.core.wire.parseJsonOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One swimmer followed for heat notifications (app.md `N-02`): the name as the start list
 * writes it — a lane's name, a relay team, or a relay leg — and the club it was found with.
 * The server matches both after `S-09`'s fold.
 */
data class FollowedSwimmer(val name: String, val club: String)

/** `N-02`'s **Upcoming**: minutes before the estimated start, or heats before. */
data class FollowLead(val byHeats: Boolean, val value: Int) {
    companion object {
        val MINUTE_CHOICES = listOf(5, 10, 15)
        val HEAT_CHOICES = listOf(1, 2, 3)
        val STANDARD = FollowLead(byHeats = false, value = 5)
    }
}

/** One meet's follows on this device (`N-02`), kept until the meet is gone (`N-09`). */
data class MeetFollows(
    val swimmers: List<FollowedSwimmer> = emptyList(),
    val lead: FollowLead = FollowLead.STANDARD,
    /** `N-06`: also when the console reaches the heat. */
    val selected: Boolean = true,
    /** The meet's `base` (C-11) when last registered, so a new token reaches it unopened (`N-07`). */
    val base: String? = null,
    /** `N-11`: off pauses the meet's notifications; the list stays. */
    val enabled: Boolean = true,
) {
    val isEmpty: Boolean get() = swimmers.isEmpty()

    /** Someone to notify about: swimmers followed and not paused (`N-11`). */
    val isActive: Boolean get() = enabled && swimmers.isNotEmpty()

    /** A second name in another club stays a second follow. */
    fun add(s: FollowedSwimmer): MeetFollows = if (s in swimmers) this else copy(swimmers = swimmers + s)

    fun remove(s: FollowedSwimmer): MeetFollows = copy(swimmers = swimmers - s)

    fun toJson(): JsonObject = buildJsonObject {
        put(
            "swimmers",
            JsonArray(
                swimmers.map {
                    buildJsonObject {
                        put("name", it.name)
                        put("club", it.club)
                    }
                },
            ),
        )
        put("by_heats", lead.byHeats)
        put("lead", lead.value)
        put("selected", selected)
        put("enabled", enabled)
        base?.let { put("base", it) }
    }

    companion object {
        fun fromJson(o: JsonObject?): MeetFollows? {
            o ?: return null
            val swimmers = o["swimmers"].asArrayOrNull()?.mapNotNull { e ->
                val s = e.asObjectOrNull() ?: return@mapNotNull null
                val name = s["name"].asStringOrNull() ?: return@mapNotNull null
                FollowedSwimmer(name, s["club"].asStringOrNull().orEmpty())
            }.orEmpty()
            val byHeats = o["by_heats"].asBoolOrNull() ?: false
            val choices = if (byHeats) FollowLead.HEAT_CHOICES else FollowLead.MINUTE_CHOICES
            val value = o["lead"].asIntOrNull()?.takeIf { it in choices } ?: choices.first()
            return MeetFollows(
                swimmers = swimmers,
                lead = FollowLead(byHeats, value),
                selected = o["selected"].asBoolOrNull() ?: true,
                // A list saved before N-11 has no `enabled`: it was on.
                enabled = o["enabled"].asBoolOrNull() ?: true,
                base = o["base"].asStringOrNull(),
            )
        }

        /**
         * `N-03`: a filter chip names a swimmer, not a club; the follow carries the club the
         * start list gives that name — one follow per club when two swimmers share it.
         */
        fun swimmersNamed(name: String, heats: List<ScheduleHeat>): List<FollowedSwimmer> = heats
            .flatMap { it.lanes }
            .filter { l -> l.name == name || l.swimmers.any { it.name == name } }
            .map { it.club }
            .distinct()
            .map { FollowedSwimmer(name, it) }
    }
}

/** The device's follows, by server and meet: a meet id means something only on its server. */
interface FollowStore {
    fun load(): Map<String, MeetFollows>
    fun save(all: Map<String, MeetFollows>)

    fun get(server: String, meetId: String): MeetFollows = load()[key(server, meetId)] ?: MeetFollows()

    /** An empty list is no row. */
    fun set(server: String, meetId: String, follows: MeetFollows?) {
        val all = load().toMutableMap()
        if (follows == null || follows.isEmpty) all.remove(key(server, meetId)) else all[key(server, meetId)] = follows
        save(all)
    }

    companion object {
        fun key(server: String, meetId: String): String = "$server|$meetId"

        /** `server|meet` back into its two halves; null for anything else. */
        fun split(key: String): Pair<String, String>? {
            val bar = key.lastIndexOf('|').takeIf { it > 0 } ?: return null
            return key.substring(0, bar) to key.substring(bar + 1)
        }

        /** The whole store as one JSON text, for a platform that keeps a string. */
        fun encode(all: Map<String, MeetFollows>): String = JsonObject(all.mapValues { it.value.toJson() }).toString()

        fun decode(text: String?): Map<String, MeetFollows> {
            val o = text?.let(::parseJsonOrNull).asObjectOrNull() ?: return emptyMap()
            return o.mapNotNull { (k, v) -> MeetFollows.fromJson(v.asObjectOrNull())?.let { k to it } }.toMap()
        }
    }
}

class InMemoryFollowStore(private var all: Map<String, MeetFollows> = emptyMap()) : FollowStore {
    override fun load(): Map<String, MeetFollows> = all
    override fun save(all: Map<String, MeetFollows>) {
        this.all = all
    }
}

/** Whether the spectator lets the app notify (`N-04`). */
enum class PushPermission { NOT_ASKED, ALLOWED, REFUSED }

/** The `PUT /meet/{id}/follow` body (api.md §5.13): every follow at once (`N-07`). */
data class FollowRegistration(val token: String, val lang: String, val follows: MeetFollows) {
    fun toJson(): JsonObject = buildJsonObject {
        put("token", token)
        put("platform", "fcm")
        put("sandbox", false)
        put("lang", lang)
        put(
            "swimmers",
            // N-11: paused, the node is told to stop; the device keeps the list.
            JsonArray(
                (if (follows.enabled) follows.swimmers else emptyList()).map {
                    buildJsonObject {
                        put("name", it.name)
                        put("club", it.club)
                    }
                },
            ),
        )
        put(
            "lead",
            buildJsonObject {
                put(if (follows.lead.byHeats) "heats" else "minutes", JsonPrimitive(follows.lead.value))
            },
        )
        put("selected", follows.selected)
    }
}

/** What a `PUT /meet/{id}/follow` came to. [MOVED] is a `409`: the meet is held elsewhere. */
enum class FollowResult { OK, GONE, MOVED, FAILED }

/** A heat a tapped notification points at (`N-08`). */
data class HeatFocus(val meetId: String, val event: String, val heat: String)
