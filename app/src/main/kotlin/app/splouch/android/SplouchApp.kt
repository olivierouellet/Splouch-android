package app.splouch.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.splouch.android.platform.FileBundleCache
import app.splouch.android.platform.NetworkWatcher
import app.splouch.android.platform.NsdBrowser
import app.splouch.android.platform.OkHttpTransport
import app.splouch.android.platform.PrefsPreferencesStore
import app.splouch.android.platform.PrefsVidStore
import app.splouch.core.session.AppModel
import app.splouch.core.session.ServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale

/**
 * Owns the one [AppModel] for the process, so a rotation or a recreated Activity never
 * reconnects a socket. The platform signals the model needs — foreground, background,
 * network restored, nearby servers — are wired here.
 */
class SplouchApp : Application() {

    /** The one URL the app ships knowing (app.md P-11). Everything else arrives as data. */
    val defaultServer: ServerAddress = ServerAddress.parseOrNull(DEFAULT_SERVER)!!

    val transport = OkHttpTransport()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var model: AppModel private set
    private lateinit var nsd: NsdBrowser
    val images = ImageCache(transport)

    override fun onCreate() {
        super.onCreate()
        model = AppModel(
            defaultServer = defaultServer,
            http = transport,
            transport = transport,
            vidStore = PrefsVidStore(this),
            prefsStore = PrefsPreferencesStore(this),
            bundleCache = FileBundleCache(this),
            scope = scope,
            deviceLang = Locale.getDefault().language.ifEmpty { "en" },
        )
        nsd = NsdBrowser(this) { model.setDiscovered(it) }
        NetworkWatcher(this) { model.networkRestored() }.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { model.foreground(); nsd.start() }
            override fun onStop(owner: LifecycleOwner) { model.background(); nsd.stop() }
        })
        model.start()
    }

    companion object {
        const val DEFAULT_SERVER = "https://splouch.ca"
    }
}

/** Picker images and logo (P-02, P-05): fetched once, decoded, kept in memory. */
class ImageCache(private val transport: OkHttpTransport) {
    private val cache = LruCache<String, Bitmap>(32)
    private val failed = HashSet<String>()

    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        if (url in failed) return null
        val bytes = transport.bytes(url) ?: run { failed += url; return null }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run { failed += url; return null }
        cache.put(url, bmp)
        return bmp
    }
}
