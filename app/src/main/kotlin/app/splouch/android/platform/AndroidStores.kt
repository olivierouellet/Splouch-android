package app.splouch.android.platform

import android.content.Context
import app.splouch.core.session.Appearance
import app.splouch.core.session.MeetTab
import app.splouch.core.session.Preferences
import app.splouch.core.session.PreferencesStore
import app.splouch.core.session.VidStore
import app.splouch.core.strings.BundleCache
import app.splouch.core.strings.CachedBundle
import app.splouch.core.strings.Labels
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
        // A device that stored the old "meet default" (absent) reads as the new default.
        labelStyle = if (prefs.getString("label_style", null) == Labels.SHORT) Labels.SHORT else Labels.DEFAULT,
        // P-15. A device that stored its preferences before this key existed was seeing a
        // pinned-dark app, so that is what it keeps.
        appearance = Appearance.parse(prefs.getString("appearance", null)),
        tab = storedTab(),
    )

    /**
     * A-04, by identity since A-11. A device that stored its tab as an index — every
     * release before this one — is read once through the tab order it was written in, so a
     * spectator who left the app on Schedule comes back to Schedule and not to page 2 of
     * whatever this meet turns out to have. The next [save] writes it back as a name.
     */
    private fun storedTab(): MeetTab? = when {
        !prefs.contains("tab") -> null
        else -> try {
            MeetTab.parse(prefs.getString("tab", null))
        } catch (_: ClassCastException) {
            MeetTab.entries.getOrNull(prefs.getInt("tab", 0))
        }
    }

    override fun save(prefs: Preferences) {
        this.prefs.edit()
            .putString("server", prefs.server)
            .putString("servers", prefs.servers.joinToString("\n"))
            .putString("lang", prefs.lang)
            .putString("label_style", prefs.labelStyle)
            .putString("appearance", prefs.appearance.name)
            .apply {
                val tab = prefs.tab
                if (tab == null) remove("tab") else putString("tab", tab.name)
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
