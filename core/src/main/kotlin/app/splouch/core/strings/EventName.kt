package app.splouch.core.strings

import app.splouch.core.wire.EventNameParts

/**
 * An event name in the reader's language (app.md T-11): a lookup in the `event_name`
 * vocabulary and a join — never a re-parse, which is the server's job.
 */
object EventName {
    /** Null when the parts compose to nothing; the caller falls back to `event_name`. */
    fun compose(parts: EventNameParts?, vocab: Map<String, String>): String? {
        if (parts == null || vocab.isEmpty()) return null
        // A kind (the console's test race) is that word alone; `raw`, the meet's
        // language, when this vocabulary predates the key.
        if (parts.kind.isNotEmpty()) return (vocab[parts.kind] ?: parts.raw).ifEmpty { null }
        fun word(k: String) = if (k.isEmpty()) "" else vocab[k] ?: k
        val left = ArrayList<String>(3)
        // The short vocabulary (`S-01`) has an empty unit: no unit, not the default.
        val unit = vocab["unit"] ?: "m"
        if (parts.dist.isNotEmpty()) left += if (unit.isEmpty()) parts.dist else parts.dist + " " + unit
        // And a word for a relay's medley, which is not an IM.
        val stroke =
            if (parts.stroke == "medley" && parts.relay && !vocab["medley_relay"].isNullOrEmpty()) {
                "medley_relay"
            } else {
                parts.stroke
            }
        if (stroke.isNotEmpty()) left += word(stroke)
        // An empty relay word is no word, not a blank slot between two spaces.
        if (parts.relay) vocab["relay"]?.takeIf { it.isNotEmpty() }?.let { left += it }
        val age = parts.age.ifEmpty { word(parts.ageKey) }
        val right = listOf(word(parts.gender), age).filter { it.isNotEmpty() }.joinToString(" ")
        val l = left.joinToString(" ")
        val sep = vocab["separator"] ?: "  —  "
        var composed = when {
            l.isNotEmpty() && right.isNotEmpty() -> l + sep + right
            else -> l.ifEmpty { right.ifEmpty { parts.raw } }
        }
        if (parts.round.isNotEmpty()) composed += sep + word(parts.round)
        return composed.ifEmpty { null }
    }

    /** The name to show for a payload carrying both shapes. */
    fun display(eventName: String, parts: EventNameParts?, vocab: Map<String, String>): String =
        compose(parts, vocab) ?: eventName
}
