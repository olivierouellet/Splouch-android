package app.splouch.core.session

import java.util.UUID

/** What is kept per server origin for C-10: the id, the day it was made, and the spectator's choice. */
data class VidEntry(
    val id: String? = null,
    /** Epoch milliseconds the id was made; null for an id stored before dates were kept. */
    val createdAt: Long? = null,
    /** C-10's setting: on by default, so absent reads as on. */
    val counting: Boolean = true,
)

/**
 * The anonymous attendance id sent with `join_meet` (app.md C-10). Binding rules: a fresh
 * random UUID **per server**, created the first time a `join_meet` goes to that origin
 * while counting is allowed, never derived from the device or from another server's id.
 *
 * **The spectator may refuse.** Off deletes the id and [vid] answers null, so the join goes
 * without one; back on makes a new id, never the old. An id older than [MAX_AGE_MS] is
 * replaced. The platform only stores [VidEntry]; every rule is here, once.
 */
abstract class VidStore(private val now: () -> Long) {
    protected abstract fun load(origin: String): VidEntry
    protected abstract fun store(origin: String, entry: VidEntry)

    /**
     * The id for [origin] (`ServerAddress.origin`), created or replaced as needed, or null
     * while counting is refused. An id from before dates were kept is dated now rather than
     * replaced, so nobody is counted twice the day this shipped — as the web's `count.js`.
     */
    fun vid(origin: String): String? {
        synchronized(this) {
            val e = load(origin)
            if (!e.counting) {
                if (e.id != null || e.createdAt != null) store(origin, VidEntry(counting = false))
                return null
            }
            val id = e.id?.takeIf(String::isNotEmpty)
            val at = e.createdAt
            return when {
                id != null && at == null -> id.also { store(origin, e.copy(createdAt = now())) }
                id != null && at != null && now() - at <= MAX_AGE_MS -> id
                else -> fresh().also { store(origin, VidEntry(it, now(), counting = true)) }
            }
        }
    }

    /** C-10's setting for [origin]; on until the spectator turns it off. */
    fun counting(origin: String): Boolean = synchronized(this) { load(origin).counting }

    /** Off forgets the id at once; on makes none until the next `join_meet` asks for one. */
    fun setCounting(origin: String, on: Boolean) = synchronized(this) {
        store(origin, if (on) load(origin).copy(counting = true) else VidEntry(counting = false))
    }

    companion object {
        /** ~13 months, the lifetime C-10 caps an id at (the web uses the same 395 days). */
        const val MAX_AGE_MS = 395L * 24 * 3600 * 1000

        fun fresh(): String = UUID.randomUUID().toString()
    }
}

class InMemoryVidStore(now: () -> Long = System::currentTimeMillis) : VidStore(now) {
    /** Open to tests, which seed ids from before dates were kept. */
    val entries = HashMap<String, VidEntry>()

    override fun load(origin: String): VidEntry = entries[origin] ?: VidEntry()
    override fun store(origin: String, entry: VidEntry) {
        entries[origin] = entry
    }
}
