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
import app.splouch.android.platform.dropNoticeFolds
import app.splouch.core.session.AppModel
import app.splouch.core.session.ServerAddress
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns the one [AppModel] for the process, so a rotation or a recreated Activity never
 * reconnects a socket. The platform signals the model needs — foreground, background,
 * network restored — are wired here, and the mDNS browse the server sheet starts (P-12).
 */
class SplouchApp : Application() {

    /** The one URL the app ships knowing (app.md P-11). Everything else arrives as data. */
    val defaultServer: ServerAddress = ServerAddress.parseOrNull(DEFAULT_SERVER)!!

    val transport = OkHttpTransport()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var model: AppModel private set
    val images = ImageCache(transport)

    override fun onCreate() {
        super.onCreate()
        dropNoticeFolds(this)
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
        model.serverBrowser = NsdBrowser(this) { model.setDiscovered(it) }
        NetworkWatcher(this) { model.networkRestored() }.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                model.foreground()
            }
            override fun onStop(owner: LifecycleOwner) {
                model.background()
            }
        })
        model.start()
    }

    companion object {
        const val DEFAULT_SERVER = "https://splouch.ca"
    }
}

/**
 * Picker images and logo (P-02, P-05): fetched once, decoded, kept in memory.
 *
 * Both ends are bounded, since the bytes are a server's: the download by [MAX_BYTES], and
 * the decode by sampling down to [MAX_SIDE] — a few-kilobyte PNG can declare a size whose
 * pixels alone would not fit in the process. The cache is budgeted in bytes, not entries.
 */
class ImageCache(private val transport: OkHttpTransport) {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val failed = HashSet<String>()

    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        if (url in failed) return null
        val bmp = transport.bytes(url, MAX_BYTES)?.let(::decode) ?: run {
            failed += url
            return null
        }
        cache.put(url, bmp)
        return bmp
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        const val MAX_BYTES = 4L * 1024 * 1024
        const val MAX_SIDE = 1024
        const val CACHE_BYTES = 32 * 1024 * 1024
    }
}
