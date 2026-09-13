package app.splouch.core.session

import java.util.UUID

/**
 * The anonymous attendance id sent with `join_meet` (app.md C-10). Binding rules: a fresh
 * random UUID **per server**, created the first time a `join_meet` goes to that origin,
 * stored once, never derived from the device or from another server's id.
 */
interface VidStore {
    /** The id for [origin] (`ServerAddress.origin`), created on first use. */
    fun vid(origin: String): String

    companion object {
        fun fresh(): String = UUID.randomUUID().toString()
    }
}

class InMemoryVidStore : VidStore {
    private val ids = HashMap<String, String>()
    override fun vid(origin: String): String = synchronized(ids) { ids.getOrPut(origin) { VidStore.fresh() } }
}
