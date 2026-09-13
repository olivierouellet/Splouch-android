package app.splouch.core.session

import app.splouch.core.wire.ServerKind
import java.net.URLEncoder

/**
 * Where a session's calls go (app.md §0.2): the same app, different addresses. A Pi has
 * one meet and no rooms; a cloud routes by `meet_id` and needs `join_meet`.
 */
data class MeetContext(val server: ServerAddress, val kind: ServerKind, val meetId: String?) {

    /** C-02: `join_meet` goes to a cloud only. */
    val joinsMeet: Boolean get() = kind == ServerKind.CLOUD && meetId != null

    val configUrl: String get() = server.httpUrl(if (kind == ServerKind.PI) "/config" else "/meet/${enc(meetId)}/config")
    val scheduleUrl: String get() = server.httpUrl(if (kind == ServerKind.PI) "/schedule.json" else "/meet/${enc(meetId)}/schedule")
    fun i18nUrl(lang: String): String = server.httpUrl("/i18n/${enc(lang)}")

    fun wsUrl(path: String): String = server.wsUrl(path)

    companion object {
        fun enc(s: String?): String = URLEncoder.encode(s.orEmpty(), "UTF-8").replace("+", "%20")
    }
}
