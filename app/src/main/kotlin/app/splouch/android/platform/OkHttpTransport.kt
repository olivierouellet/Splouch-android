package app.splouch.android.platform

import app.splouch.core.session.HttpClient
import app.splouch.core.session.HttpFailure
import app.splouch.core.session.HttpResponse
import app.splouch.core.transport.WebSocketTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OkHttp behind the two interfaces the core needs. One client, one connection pool. */
class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : WebSocketTransport, HttpClient {

    override fun connect(url: String, listener: WebSocketTransport.Listener): WebSocketTransport.Handle {
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure(t)
        })
        return object : WebSocketTransport.Handle {
            override fun send(text: String): Boolean = ws.send(text)
            override fun close() { ws.close(1000, null) }
            override fun abort() = ws.cancel()
        }
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        try {
            client.newCall(req).execute().use { r ->
                HttpResponse(r.code, r.body.string(), r.headers.names().associateWith { r.header(it).orEmpty() })
            }
        } catch (e: IOException) {
            throw HttpFailure(e.message ?: "network error", e)
        }
    }

    /** Raw bytes, for the picker images (P-02, P-05). Null on any fault. */
    suspend fun bytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
        } catch (_: IOException) {
            null
        }
    }
}
