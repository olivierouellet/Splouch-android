package app.splouch.android.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper

/** C-05: the network came back — the owner probes its sockets. */
class NetworkWatcher(context: Context, private val onRestored: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var first = true

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // The first callback describes the network we already have; every later one is a change.
            if (first) { first = false; return }
            main.post(onRestored)
        }
    }

    fun start() {
        try { cm.registerDefaultNetworkCallback(callback) } catch (_: Exception) { }
    }
}
