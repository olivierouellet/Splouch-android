package app.splouch.core.support

import app.splouch.core.session.HttpClient
import app.splouch.core.session.HttpFailure
import app.splouch.core.session.HttpResponse

/** Canned responses by exact URL; anything else is a network fault. */
class StubHttp : HttpClient {
    val routes = HashMap<String, HttpResponse>()
    val calls = ArrayList<Pair<String, Map<String, String>>>()

    fun on(url: String, status: Int = 200, body: String = "", headers: Map<String, String> = emptyMap()) {
        routes[url] = HttpResponse(status, body, headers)
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        calls += url to headers
        return routes[url] ?: throw HttpFailure("no route for $url")
    }
}
