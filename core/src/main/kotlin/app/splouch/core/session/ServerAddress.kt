package app.splouch.core.session

import java.net.URI

/**
 * A server's base URL, normalised so two spellings of one server are one server: scheme
 * and host lowercased, a default port dropped, no path, query or fragment. [origin] is
 * the key a per-server `vid` is stored under (app.md C-10).
 *
 * Cleartext (app.md P-12): `http` is accepted only for a host on the local network by
 * name — `.local` (the Pi's mDNS names) — or the loopback and emulator addresses a
 * developer uses. Anything remote must be `https`. This matches the app's network
 * security config, which lists the same names; it cannot express IP ranges, so a Pi is
 * dialled by its `.local` name, never by a raw address.
 */
data class ServerAddress private constructor(val scheme: String, val host: String, val port: Int) {

    val origin: String get() = "$scheme://$host" + if (port > 0) ":$port" else ""
    val isCleartext: Boolean get() = scheme == "http"

    fun httpUrl(path: String): String = origin + path
    fun wsUrl(path: String): String = (if (scheme == "https") "wss" else "ws") + "://" + host + (if (port > 0) ":$port" else "") + path

    /** For the header: host and port, without the scheme (P-11's "show the server" rule). */
    val display: String get() = host + if (port > 0) ":$port" else ""

    override fun toString(): String = origin

    sealed interface Result {
        data class Ok(val address: ServerAddress) : Result
        data object Invalid : Result
        /** `http` to a host that is not on the local network. */
        data object CleartextNotLocal : Result
    }

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1", "[::1]")

        fun parse(text: String): Result {
            var s = text.trim()
            if (s.isEmpty()) return Result.Invalid
            if (!s.contains("://")) s = "https://$s"
            val uri = try { URI(s) } catch (_: Exception) { return Result.Invalid }
            val scheme = uri.scheme?.lowercase() ?: return Result.Invalid
            if (scheme != "http" && scheme != "https") return Result.Invalid
            val host = uri.host?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return Result.Invalid
            if (uri.userInfo != null) return Result.Invalid
            var port = uri.port
            if (port == (if (scheme == "https") 443 else 80)) port = -1
            if (scheme == "http" && !isLocalName(host)) return Result.CleartextNotLocal
            return Result.Ok(ServerAddress(scheme, host, if (port > 0) port else -1))
        }

        fun parseOrNull(text: String): ServerAddress? = (parse(text) as? Result.Ok)?.address

        fun isLocalName(host: String): Boolean = host.endsWith(".local") || host in LOCAL_HOSTS
    }
}
