package app.splouch.core.session

import java.net.URI
import java.net.URLDecoder

/**
 * P-16: the address behind a QR code, `https://<default host>/add?server=<origin>`.
 *
 * **Why an `https` link on the app's own host and not a `splouch://` scheme.** The reader
 * uses the camera they already have — the stock Android scanner, Lens, a third-party app —
 * and none of those will open a private scheme from a code taped to a pool wall; several
 * refuse it outright as unsafe. An `https` URL they all open, and it is the only shape
 * that answers the case the poster is printed for: **the app is not installed yet**. Then
 * nothing intercepts the link, the browser lands on the page, and the page offers the
 * store. That fallback is the web half's job and lives in the `Splouch` repo — see
 * `parity.md` `P-16` for what it must serve, including the `assetlinks.json` without
 * which Android shows a chooser instead of opening the app.
 *
 * **The host is the app's own default server** ([AppModel.defaultServer]), the one URL the
 * app ships knowing (`P-11`), and the manifest's verified App Link host is the same name.
 * A link naming any other host is not ours and does not parse — a server cannot mint a
 * code that adds a *different* server, and the poster at a pool carries the pool's Pi in
 * the query rather than in the authority.
 *
 * The address inside is held to exactly the rule a typed one is ([ServerAddress.parse]):
 * `http` for a `.local` name or the developer loopbacks, `https` for anything else. A
 * printed code is a stranger's input in a way a typed address is not, so the cleartext
 * floor cannot be lower here — and the link only *proposes*. Nothing is saved until the
 * reader says yes and `GET /server` answers (`P-13`).
 */
object ServerLink {

    /** The path the App Link is verified for; the manifest declares this exact string. */
    const val PATH = "/add"

    /** The query parameter carrying the server's origin. */
    const val PARAM = "server"

    sealed interface Result {
        data class Ok(val address: ServerAddress) : Result
        /** Not our link, or ours with nothing usable in it. Either way there is no server here. */
        data object Invalid : Result
        /** A real address, but `http` to a host that is not on the local network. */
        data object CleartextNotLocal : Result
    }

    /**
     * [url] is what the intent carried; [host] is the app's default server's host, the only
     * authority a link may name.
     */
    fun parse(url: String, host: String): Result {
        val uri = try { URI(url.trim()) } catch (_: Exception) { return Result.Invalid }
        if (uri.scheme?.lowercase() != "https") return Result.Invalid
        if (!uri.host.equals(host, ignoreCase = true)) return Result.Invalid
        if (uri.path?.trimEnd('/') != PATH) return Result.Invalid
        val server = param(uri.rawQuery, PARAM) ?: return Result.Invalid
        return when (val r = ServerAddress.parse(server)) {
            is ServerAddress.Result.Ok -> Result.Ok(r.address)
            ServerAddress.Result.CleartextNotLocal -> Result.CleartextNotLocal
            ServerAddress.Result.Invalid -> Result.Invalid
        }
    }

    /** The last value for [name], percent-decoded; null when it is absent or empty. */
    private fun param(rawQuery: String?, name: String): String? =
        rawQuery.orEmpty().split('&')
            .mapNotNull { pair ->
                if (!pair.startsWith("$name=")) null
                else try { URLDecoder.decode(pair.substring(name.length + 1), "UTF-8") } catch (_: Exception) { null }
            }
            .lastOrNull()
            ?.takeIf { it.isNotBlank() }
}
