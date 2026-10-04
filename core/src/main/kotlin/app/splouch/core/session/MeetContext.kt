package app.splouch.core.session

import app.splouch.core.wire.ServerKind
import java.net.URLEncoder

/**
 * Where a session's calls go (app.md §0.2): the same app, different addresses. A Pi has
 * one meet and no rooms; a cloud routes by `meet_id` and needs `join_meet`.
 *
 * [server] is the server the meet list came from: its `vid` (C-10) and its strings. [base]
 * is where the meet itself is reached (C-11) — the server URL unless `GET /meets` named
 * another, and the target of a `moved` (C-12).
 */
data class MeetContext(
    val server: ServerAddress,
    val kind: ServerKind,
    val meetId: String?,
    val base: MeetBase = MeetBase.of(server),
) {

    /** C-02: `join_meet` goes to a cloud only. */
    val joinsMeet: Boolean get() = kind == ServerKind.CLOUD && meetId != null

    val configUrl: String get() = base.httpUrl(
        if (kind == ServerKind.PI) {
            "/config"
        } else {
            "/meet/${enc(meetId)}/config"
        },
    )
    val scheduleUrl: String get() = base.httpUrl(
        if (kind == ServerKind.PI) {
            "/schedule.json"
        } else {
            "/meet/${enc(meetId)}/schedule"
        },
    )

    /** The meet's icon, cloud only (api.md §4). No screen draws it yet. */
    val iconUrl: String? get() = meetId?.takeIf { kind == ServerKind.CLOUD }?.let { base.httpUrl("/icon/${enc(it)}") }

    fun i18nUrl(lang: String): String = server.httpUrl("/i18n/${enc(lang)}")

    fun wsUrl(path: String): String = base.wsUrl(path)

    companion object {
        fun enc(s: String?): String = URLEncoder.encode(s.orEmpty(), "UTF-8").replace("+", "%20")
    }
}
