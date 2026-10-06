package app.splouch.android.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import app.splouch.core.session.KnownServer
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.ServerBrowser
import app.splouch.core.wire.ServerKind

/**
 * P-12: browse for `_splouch._tcp` and offer what answers, by its `.local` host name.
 *
 * The app dials a Pi by name, never by address (see the network security config), and
 * a service's host name is only exposed by the platform from Android 16 on, or from 13
 * with a recent Mainline update ([hostname]). Elsewhere a Pi is still found but cannot
 * be offered; it can be added by hand as `http://splouch.local:5000` (P-13).
 */
class NsdBrowser(context: Context, private val onChange: (List<KnownServer>) -> Unit) : ServerBrowser {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, KnownServer>()
    private var listener: NsdManager.DiscoveryListener? = null

    /**
     * Bumped on every start and stop. NSD answers on its own thread and may answer after a
     * stop; anything posted under an older generation is dropped, so a late resolve cannot
     * slip into the next scan's list.
     */
    private var generation = 0

    /**
     * `resolveService` takes one request at a time — a second while one is out fails with
     * `FAILURE_ALREADY_ACTIVE` — so with two Pis on the wifi the second would never be
     * offered. Found services wait here and are resolved in turn, all on the main thread.
     */
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    override fun start() {
        if (listener != null || nsd == null) return
        val gen = ++generation
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { if (gen == generation) listener = null }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    if (gen != generation) return@post
                    pending.addLast(info)
                    resolveNext(gen)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post {
                    if (gen == generation && found.remove(info.serviceName) != null) onChange(found.values.toList())
                }
            }
        }
        listener = l
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (
            _: Exception,
        ) {
            listener = null
        }
    }

    override fun stop() {
        generation++
        listener?.let {
            try {
                nsd?.stopServiceDiscovery(it)
            } catch (_: Exception) { }
        }
        listener = null
        found.clear()
        pending.clear()
        resolving = false
    }

    private fun resolveNext(gen: Int) {
        if (resolving || gen != generation) return
        val info = pending.removeFirstOrNull() ?: return
        resolving = true
        resolve(info) { server ->
            main.post {
                if (gen != generation) return@post
                resolving = false
                if (server != null) {
                    found[server.name] = server
                    onChange(found.values.toList())
                }
                resolveNext(gen)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo, done: (KnownServer?) -> Unit) {
        val nsd = nsd ?: return done(null)
        try {
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done(null)
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) = done(toServer(serviceInfo))
                },
            )
        } catch (_: Exception) {
            done(null)
        }
    }

    private fun toServer(info: NsdServiceInfo): KnownServer? {
        val name = hostname(info)?.trimEnd('.') ?: return null
        val port = info.port.takeIf { it > 0 } ?: 5000
        val address = ServerAddress.parseOrNull("http://$name:$port") ?: return null
        val kind = ServerKind.fromWire(info.attributes["kind"]?.toString(Charsets.UTF_8)) ?: ServerKind.PI
        return KnownServer(address, info.serviceName, kind, KnownServer.Source.DISCOVERED)
    }

    /**
     * `NsdServiceInfo.getHostname` is API 36, or API 33+ with T-extension 17 delivered by a
     * Mainline update. An Android 14 or 15 device without that update has no such method,
     * and calling it there is a `NoSuchMethodError` — an `Error`, which no `catch
     * (Exception)` stops, on NSD's thread: a crash on tapping Search.
     */
    private fun hostname(info: NsdServiceInfo): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> info.hostname
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 17 -> info.hostname
        else -> null
    }

    companion object {
        const val SERVICE_TYPE = "_splouch._tcp."
    }
}
