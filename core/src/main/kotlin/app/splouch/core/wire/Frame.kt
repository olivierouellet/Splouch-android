package app.splouch.core.wire

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One WebSocket text frame, both directions: `{ "event": "<name>", "data": <any> }` (api.md §1).
 *
 * `data` is whatever the server sent — an object, a bare string, or nothing at all
 * (`schedule_update` carries no `data` key). A frame with no `data` encodes without one,
 * which is how the reference client sends `ping`.
 */
data class Frame(val event: String, val data: JsonElement? = null) {

    fun encode(): String = buildJsonObject {
        put("event", event)
        if (data != null) put("data", data)
    }.toString()

    companion object {
        /** Null for anything that is not a frame: bad JSON, no `event`, or a non-string one. */
        fun decode(text: String): Frame? {
            val obj = parseJsonOrNull(text) as? JsonObject ?: return null
            val event = (obj["event"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val data = obj["data"]?.takeUnless { it is JsonNull }
            return Frame(event, data)
        }

        fun ping(): Frame = Frame("ping")

        /** `join_meet {meet_id, vid}` — the cloud room handshake (api.md §3, app.md C-02). */
        fun joinMeet(meetId: String, vid: String): Frame = Frame(
            "join_meet",
            buildJsonObject {
                put("meet_id", meetId)
                put("vid", vid)
            },
        )
    }
}
