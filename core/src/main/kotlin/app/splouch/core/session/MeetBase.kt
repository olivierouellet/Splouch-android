package app.splouch.core.session

import java.net.URI

/**
 * C-11: where one meet is reached — a server origin and, on a cloud of several workers, the
 * worker's path prefix (`https://ca1.splouch.org/w2`). The meet's sockets, config, schedule
 * and icon hang off it; the meet list, the picker's chrome, `/i18n` and `/servers` stay on
 * the server the list came from.
 *
 * The origin goes through [ServerAddress.parse], so a `base` is held to P-12's cleartext
 * floor like every other address the app dials.
 */
@ConsistentCopyVisibility
data class MeetBase private constructor(val address: ServerAddress, val path: String) {

    val url: String get() = address.origin + path

    fun httpUrl(p: String): String = address.httpUrl(path + p)
    fun wsUrl(p: String): String = address.wsUrl(path + p)

    override fun toString(): String = url

    companion object {
        /** No `base` from the server (older than app.md v3): the server URL itself. */
        fun of(server: ServerAddress): MeetBase = MeetBase(server, "")

        /**
         * A `base` as the server sent it, or null when there is none to follow: absent,
         * blank, not an absolute `http(s)` URL, or `http` to a host off the local network.
         * The caller keeps what it had.
         */
        fun parse(text: String?): MeetBase? {
            val s = text?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
            val uri = try {
                URI(s)
            } catch (_: Exception) {
                return null
            }
            if (uri.scheme == null || uri.rawAuthority == null || uri.rawQuery != null || uri.rawFragment != null) {
                return null
            }
            val address = ServerAddress.parseOrNull("${uri.scheme}://${uri.rawAuthority}") ?: return null
            return MeetBase(address, uri.rawPath.orEmpty().trimEnd('/'))
        }
    }
}
