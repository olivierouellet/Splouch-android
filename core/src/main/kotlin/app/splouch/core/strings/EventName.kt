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
        fun word(k: String) = if (k.isEmpty()) "" else vocab[k] ?: k
        val left = ArrayList<String>(3)
        if (parts.dist.isNotEmpty()) left += parts.dist + " " + (vocab["unit"] ?: "m")
        if (parts.stroke.isNotEmpty()) left += word(parts.stroke)
        if (parts.relay) vocab["relay"]?.let { left += it }
        val age = parts.age.ifEmpty { word(parts.ageKey) }
        val right = listOf(word(parts.gender), age).filter { it.isNotEmpty() }.joinToString(" ")
        val l = left.joinToString(" ")
        val composed = when {
            l.isNotEmpty() && right.isNotEmpty() -> l + (vocab["separator"] ?: "  —  ") + right
            else -> l.ifEmpty { right.ifEmpty { parts.raw } }
        }
        return composed.ifEmpty { null }
    }

    /** The name to show for a payload carrying both shapes. */
    fun display(eventName: String, parts: EventNameParts?, vocab: Map<String, String>): String =
        compose(parts, vocab) ?: eventName
}
