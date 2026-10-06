package app.splouch.android.platform

import app.splouch.core.session.HttpClient
import app.splouch.core.session.HttpFailure
import app.splouch.core.session.HttpResponse
import app.splouch.core.session.ServerAddress
import app.splouch.core.transport.WebSocketTransport
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** OkHttp behind the two interfaces the core needs. One client, one connection pool. */
class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(LocalCleartextOnly)
        .addNetworkInterceptor(LocalCleartextOnly)
        .addNetworkInterceptor(SameOriginRedirects)
        // Calls are enqueued (see [await]), and OkHttp holds a sixth to one host until one
        // of five ends — a meet's config, schedule, strings and images all share a host.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .build(),
) : WebSocketTransport,
    HttpClient {

    override fun connect(url: String, listener: WebSocketTransport.Listener): WebSocketTransport.Handle {
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
                override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure(t)
            },
        )
        return object : WebSocketTransport.Handle {
            override fun send(text: String): Boolean = ws.send(text)
            override fun close() {
                ws.close(1000, null)
            }
            override fun abort() = ws.cancel()
        }
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return try {
            client.newCall(req).await { r ->
                val headers = r.headers.names().associateWith { r.header(it).orEmpty() }
                HttpResponse(r.code, r.body.text(MAX_TEXT_BYTES), headers)
            }
        } catch (e: IOException) {
            throw HttpFailure(e.message ?: "network error", e)
        }
    }

    override suspend fun put(url: String, json: String): HttpResponse {
        val req = Request.Builder().url(url).put(json.toRequestBody(JSON)).build()
        return try {
            client.newCall(req).await { r -> HttpResponse(r.code, r.body.text(MAX_TEXT_BYTES)) }
        } catch (e: IOException) {
            throw HttpFailure(e.message ?: "network error", e)
        }
    }

    /** Raw bytes, for the picker images (P-02, P-05). Null on any fault, or past [limit]. */
    suspend fun bytes(url: String, limit: Long): ByteArray? = try {
        client.newCall(Request.Builder().url(url).build()).await { r ->
            if (r.isSuccessful) r.body.capped(limit) else null
        }
    } catch (_: IOException) {
        null
    }

    private companion object {
        /**
         * A body is held in memory whole, so a server — or anything a redirect or a hostile
         * LAN put in its place — could end the process with one oversized answer. The
         * largest real one is a big meet's schedule, well under this.
         */
        const val MAX_TEXT_BYTES = 16L * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Runs the call and [read]s its response off the main thread, **cancellably**: a caller
 * that gives up — A-12's 4-second timeout, a back gesture abandoned — cancels the call,
 * which aborts the connect, the wait for headers and the body read alike. A blocking
 * `execute()` under `withContext` would instead hold the caller until the socket's own
 * timeouts, up to half a minute.
 */
private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                cont.resumeWith(runCatching { response.use(read) })
            }
        },
    )
}

/** The body, refused with an [IOException] once it passes [limit] bytes. */
private fun ResponseBody.capped(limit: Long): ByteArray {
    val source = source()
    if (source.request(limit + 1)) throw IOException("response over $limit bytes")
    return source.buffer.readByteArray()
}

private fun ResponseBody.text(limit: Long): String = String(capped(limit), contentType()?.charset() ?: Charsets.UTF_8)

/**
 * app.md P-12's cleartext floor, at the transport: plain `http` (and `ws`) only to a host
 * on the local network ([ServerAddress.isLocalName]). The network security config cannot
 * express IP ranges and so permits cleartext outright; this is what holds the line for
 * any URL that did not come through [ServerAddress.parse]. Registered twice: as an
 * application interceptor it refuses before a socket is opened, and as a network
 * interceptor it sees every redirect hop before a byte of the request is written.
 */
private object LocalCleartextOnly : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps && !ServerAddress.isLocalName(url.host)) {
            throw IOException("plain http to a host off the local network refused")
        }
        return chain.proceed(chain.request())
    }
}

/**
 * A redirect is followed only within the origin it came from. Everything the app asks for
 * is on the server the reader chose; a `Location` naming another host would let a remote
 * server steer the phone at an address on its LAN (local hosts are cleartext-permitted), or
 * answer a typed address's `GET /server` (P-13) with somebody else's.
 */
private object SameOriginRedirects : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isRedirect) return response
        val from = response.request.url
        val to = response.header("Location")?.let { from.resolve(it) } ?: return response
        if (to.scheme == from.scheme && to.host == from.host && to.port == from.port) return response
        response.close()
        throw IOException("redirect to another origin refused")
    }
}
