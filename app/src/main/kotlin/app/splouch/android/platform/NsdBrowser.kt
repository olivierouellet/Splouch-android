package app.splouch.android.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.splouch.core.session.KnownServer
import app.splouch.core.session.ServerAddress
import app.splouch.core.wire.ServerKind

/**
 * P-12: browse for `_splouch._tcp` and offer what answers, by its `.local` host name.
 *
 * The app dials a Pi by name, never by address (see the network security config), and
 * a service's host name is only exposed by the platform from Android 14 on. On older
 * devices a Pi is still found but cannot be offered; it can be added by hand as
 * `http://splouch.local:5000` (P-13).
 */
class NsdBrowser(context: Context, private val onChange: (List<KnownServer>) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, KnownServer>()
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null || nsd == null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { listener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) { resolve(info) }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { if (found.remove(info.serviceName) != null) onChange(found.values.toList()) }
            }
        }
        listener = l
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Exception) { listener = null }
    }

    fun stop() {
        listener?.let { try { nsd?.stopServiceDiscovery(it) } catch (_: Exception) { } }
        listener = null
        found.clear()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        try {
            nsd?.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) serviceInfo.hostname else null
                    val name = host?.trimEnd('.') ?: return
                    val port = serviceInfo.port.takeIf { it > 0 } ?: 5000
                    val address = ServerAddress.parseOrNull("http://$name:$port") ?: return
                    val kind = ServerKind.fromWire(serviceInfo.attributes["kind"]?.toString(Charsets.UTF_8)) ?: ServerKind.PI
                    val server = KnownServer(address, serviceInfo.serviceName, kind, KnownServer.Source.DISCOVERED)
                    main.post { found[serviceInfo.serviceName] = server; onChange(found.values.toList()) }
                }
            })
        } catch (_: Exception) { }
    }

    companion object {
        const val SERVICE_TYPE = "_splouch._tcp."
    }
}
