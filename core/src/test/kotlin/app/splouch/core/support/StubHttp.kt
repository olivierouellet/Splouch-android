package app.splouch.core.support

import app.splouch.core.session.HttpClient
import app.splouch.core.session.HttpFailure
import app.splouch.core.session.HttpResponse
import kotlinx.coroutines.awaitCancellation

/** Canned responses by exact URL; anything else is a network fault. */
class StubHttp : HttpClient {
    val routes = HashMap<String, HttpResponse>()
    val calls = ArrayList<Pair<String, Map<String, String>>>()

    /** URLs that never answer: the call suspends until its caller gives up (A-12's timeout). */
    val hanging = HashSet<String>()

    fun on(url: String, status: Int = 200, body: String = "", headers: Map<String, String> = emptyMap()) {
        routes[url] = HttpResponse(status, body, headers)
    }

    /** Every `PUT`, with its body, in order. */
    val puts = ArrayList<Pair<String, String>>()

    override suspend fun put(url: String, json: String): HttpResponse {
        puts += url to json
        return routes[url] ?: throw HttpFailure("no route for $url")
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        calls += url to headers
        if (url in hanging) awaitCancellation()
        return routes[url] ?: throw HttpFailure("no route for $url")
    }
}
