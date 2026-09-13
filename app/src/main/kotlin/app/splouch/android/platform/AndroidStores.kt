package app.splouch.android.platform

import android.content.Context
import app.splouch.core.session.Preferences
import app.splouch.core.session.PreferencesStore
import app.splouch.core.session.VidStore
import app.splouch.core.strings.BundleCache
import app.splouch.core.strings.CachedBundle
import app.splouch.core.wire.I18nBundle
import java.io.File
import java.security.MessageDigest

/**
 * C-10: one random UUID per server origin, stored once. Nothing here reads a device
 * identifier, and no id is ever derived from another.
 */
class PrefsVidStore(context: Context) : VidStore {
    private val prefs = context.getSharedPreferences("splouch.vid", Context.MODE_PRIVATE)

    override fun vid(origin: String): String = synchronized(this) {
        prefs.getString(origin, null)?.takeIf { it.isNotEmpty() } ?: VidStore.fresh().also { prefs.edit().putString(origin, it).apply() }
    }
}

class PrefsPreferencesStore(context: Context) : PreferencesStore {
    private val prefs = context.getSharedPreferences("splouch.prefs", Context.MODE_PRIVATE)

    override fun load(): Preferences = Preferences(
        server = prefs.getString("server", null),
        servers = prefs.getString("servers", "")!!.split('\n').filter { it.isNotBlank() },
        lang = prefs.getString("lang", null),
        labelStyle = prefs.getString("label_style", null),
        tab = if (prefs.contains("tab")) prefs.getInt("tab", 0) else null,
    )

    override fun save(prefs: Preferences) {
        this.prefs.edit()
            .putString("server", prefs.server)
            .putString("servers", prefs.servers.joinToString("\n"))
            .putString("lang", prefs.lang)
            .putString("label_style", prefs.labelStyle)
            .apply {
                val tab = prefs.tab
                if (tab == null) remove("tab") else putInt("tab", tab)
            }
            .apply()
    }
}

/** T-10's disk cache: the last `GET /i18n/{lang}` body per server and language, with its ETag. */
class FileBundleCache(context: Context) : BundleCache {
    private val dir = File(context.filesDir, "i18n").apply { mkdirs() }

    private fun base(origin: String, lang: String): String {
        val h = MessageDigest.getInstance("SHA-1").digest(origin.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        return "${h}_${lang.filter { it.isLetterOrDigit() || it == '-' }}"
    }

    override fun read(origin: String, lang: String): CachedBundle? {
        val b = base(origin, lang)
        val body = File(dir, "$b.json").takeIf { it.isFile }?.readText() ?: return null
        val bundle = I18nBundle.fromText(body) ?: return null
        val etag = File(dir, "$b.etag").takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        return CachedBundle(bundle, etag)
    }

    override fun write(origin: String, lang: String, text: String, etag: String?) {
        if (I18nBundle.fromText(text) == null) return
        val b = base(origin, lang)
        File(dir, "$b.json").writeText(text)
        val e = File(dir, "$b.etag")
        if (etag.isNullOrBlank()) e.delete() else e.writeText(etag)
    }
}
