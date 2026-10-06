package app.splouch.android.platform

import android.content.Context
import androidx.core.content.edit
import app.splouch.core.session.Appearance
import app.splouch.core.session.MeetTab
import app.splouch.core.session.Preferences
import app.splouch.core.session.PreferencesStore
import app.splouch.core.session.VidEntry
import app.splouch.core.session.VidStore
import app.splouch.core.strings.BundleCache
import app.splouch.core.strings.CachedBundle
import app.splouch.core.strings.Labels
import app.splouch.core.wire.I18nBundle
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * C-10's storage, per normalised server origin: the id under the origin itself (where
 * every release before dates were kept wrote it), its creation date, and the spectator's
 * counting setting. Every rule — off forgets, back on makes a new one, 13-month replacement
 * — is [VidStore]'s. Nothing here reads a device identifier.
 */
class PrefsVidStore(context: Context) : VidStore(System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("splouch.vid", Context.MODE_PRIVATE)

    // An origin holds no space, so a suffixed key cannot collide with another server's id.
    override fun load(origin: String): VidEntry = VidEntry(
        id = prefs.getString(origin, null)?.takeIf { it.isNotEmpty() },
        createdAt = prefs.getLong("$origin at", 0L).takeIf { it > 0L },
        counting = prefs.getBoolean("$origin counting", true),
    )

    override fun store(origin: String, entry: VidEntry) {
        val id = entry.id
        val at = entry.createdAt
        prefs.edit {
            if (id == null) remove(origin) else putString(origin, id)
            if (at == null) remove("$origin at") else putLong("$origin at", at)
            // Only a refusal is written: on is the default, and stays so for a server never touched.
            if (entry.counting) remove("$origin counting") else putBoolean("$origin counting", false)
        }
    }
}

/**
 * P-06 used to fold, and remembered each fold per server in `splouch.notices`. The line no
 * longer folds, so the folds are deleted — once, on the first launch of this release; the
 * file is gone after that and the call finds nothing.
 */
fun dropNoticeFolds(context: Context) {
    context.deleteSharedPreferences("splouch.notices")
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
        introSeen = prefs.getBoolean("intro_seen", false),
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
            .putBoolean("intro_seen", prefs.introSeen)
            .apply {
                val tab = prefs.tab
                if (tab == null) remove("tab") else putString("tab", tab.name)
            }
            .apply()
    }
}

/**
 * T-10's disk cache: the last `GET /i18n/{lang}` body per server and language, with its ETag.
 *
 * The model reads it on the main thread every time it builds a string table, so each file
 * is read and parsed at most once per process and served from memory after that; a write
 * lands in memory at once and on disk on [io]. What is left on the main thread is the
 * first read of each language, a few kilobytes.
 */
class FileBundleCache(context: Context) : BundleCache {
    private val dir = File(context.filesDir, "i18n")
    private val memory = HashMap<String, CachedBundle?>()
    private val io = Executors.newSingleThreadExecutor()

    private fun base(origin: String, lang: String): String {
        val h = MessageDigest.getInstance("SHA-1").digest(origin.toByteArray()).joinToString("") {
            "%02x".format(it)
        }.take(16)
        return "${h}_${lang.filter { it.isLetterOrDigit() || it == '-' }}"
    }

    @Synchronized
    override fun read(origin: String, lang: String): CachedBundle? {
        val b = base(origin, lang)
        if (b in memory) return memory[b]
        val body = File(dir, "$b.json").takeIf { it.isFile }?.readText()
        val cached = body?.let(I18nBundle::fromText)?.let { bundle ->
            CachedBundle(bundle, File(dir, "$b.etag").takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() })
        }
        memory[b] = cached
        return cached
    }

    @Synchronized
    override fun write(origin: String, lang: String, text: String, etag: String?) {
        val bundle = I18nBundle.fromText(text) ?: return
        val b = base(origin, lang)
        memory[b] = CachedBundle(bundle, etag?.takeIf { it.isNotBlank() })
        io.execute {
            // A full disk loses the cache, not the app: memory holds it for this process.
            try {
                dir.mkdirs()
                File(dir, "$b.json").writeText(text)
                val e = File(dir, "$b.etag")
                if (etag.isNullOrBlank()) e.delete() else e.writeText(etag)
            } catch (_: IOException) { }
        }
    }
}
