package app.splouch.core.session

import java.net.URI

/**
 * A server's base URL, normalised so two spellings of one server are one server: scheme
 * and host lowercased, a default port dropped, no path, query or fragment. [origin] is
 * the key a per-server `vid` is stored under (app.md C-10).
 *
 * Cleartext (app.md P-12): `http` is accepted only for a host on the local network — a
 * `.local` name (the Pi's mDNS names), `localhost`, or an IP literal in a loopback,
 * private or link-local range ([isLocalName]). Anything else must be `https`. Every way
 * an address comes in passes through here before any request: typed (P-13), listed by
 * `GET /servers`, and scanned (P-16, via [ServerLink]).
 *
 * `copy()` is as private as the constructor: a public one would be a way round the
 * normalisation above.
 */
@ConsistentCopyVisibility
data class ServerAddress private constructor(val scheme: String, val host: String, val port: Int) {

    val origin: String get() = "$scheme://$host" + if (port > 0) ":$port" else ""
    val isCleartext: Boolean get() = scheme == "http"

    fun httpUrl(path: String): String = origin + path
    fun wsUrl(path: String): String =
        (if (scheme == "https") "wss" else "ws") + "://" + host + (if (port > 0) ":$port" else "") + path

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
        fun parse(text: String): Result {
            var s = text.trim()
            if (s.isEmpty()) return Result.Invalid
            if (!s.contains("://")) s = "https://$s"
            val uri = try {
                URI(s)
            } catch (_: Exception) {
                return Result.Invalid
            }
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

        /**
         * [host] lowercased: a name, a dotted IPv4 literal, or an IPv6 one, bracketed as [URI]
         * gives it or bare as OkHttp does (a name never holds a `:`). A name is local only as `localhost` or under `.local`; anything that merely
         * looks like an address (`10.0.0.1.example`, `010.0.0.1`, `3232235796`) is a name, and
         * so not local. An IPv6 zone (`%wlan0`) is ignored, and an IPv4-mapped address is
         * judged by its IPv4 half.
         */
        fun isLocalName(host: String): Boolean {
            if (host == "localhost" || host.endsWith(".local")) return true
            val ip = if (':' in host) {
                ipv6(host.removePrefix("[").removeSuffix("]").substringBefore('%'))
            } else {
                ipv4(host)
            } ?: return false
            return when (ip.size) {
                4 -> isLocalV4(ip)
                else -> isLocalV6(ip)
            }
        }

        private fun isLocalV4(b: IntArray): Boolean = b[0] == 127 || b[0] == 10 ||
            (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168) ||
            (b[0] == 169 && b[1] == 254)

        private fun isLocalV6(b: IntArray): Boolean {
            val mapped = (0 until 10).all { b[it] == 0 } && b[10] == 0xff && b[11] == 0xff
            if (mapped) return isLocalV4(b.copyOfRange(12, 16))
            return ((0 until 15).all { b[it] == 0 } && b[15] == 1) || // ::1
                (b[0] and 0xfe) == 0xfc || // fc00::/7
                (b[0] == 0xfe && (b[1] and 0xc0) == 0x80) // fe80::/10
        }

        /** Four decimal octets, no leading zeros: the only spelling every resolver reads alike. */
        private fun ipv4(s: String): IntArray? {
            val parts = s.split('.')
            if (parts.size != 4) return null
            return IntArray(4) { i -> octet(parts[i]) ?: return null }
        }

        private fun octet(p: String): Int? =
            p.takeIf { it.length in 1..3 && it.all { c -> c in '0'..'9' } && (it.length == 1 || it[0] != '0') }
                ?.toInt()?.takeIf { it <= 255 }

        /** Sixteen bytes, or null: `::` compression and a trailing dotted IPv4 allowed. */
        private fun ipv6(s: String): IntArray? {
            if (s.isEmpty()) return null
            val halves = s.split("::")
            if (halves.size > 2) return null
            fun groups(part: String): List<Int>? {
                if (part.isEmpty()) return emptyList()
                val out = mutableListOf<Int>()
                val fields = part.split(':')
                fields.forEachIndexed { i, f ->
                    if (i == fields.lastIndex && '.' in f) {
                        val v4 = ipv4(f) ?: return null
                        out += (v4[0] shl 8) or v4[1]
                        out += (v4[2] shl 8) or v4[3]
                    } else {
                        if (f.isEmpty() || f.length > 4) return null
                        out += f.toIntOrNull(16) ?: return null
                    }
                }
                return out
            }
            val head = groups(halves[0]) ?: return null
            val tail = (if (halves.size == 2) groups(halves[1]) else emptyList()) ?: return null
            if ('.' in halves[0] && halves.size == 2) return null
            val all = if (halves.size == 2) {
                if (head.size + tail.size > 7) return null
                head + List(8 - head.size - tail.size) { 0 } + tail
            } else {
                head.takeIf { it.size == 8 } ?: return null
            }
            return IntArray(16) { i -> (all[i / 2] shr (if (i % 2 == 0) 8 else 0)) and 0xff }
        }
    }
}
