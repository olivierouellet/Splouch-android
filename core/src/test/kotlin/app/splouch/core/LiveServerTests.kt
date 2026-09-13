package app.splouch.core

import app.splouch.core.session.ApiResult
import app.splouch.core.session.HttpClient
import app.splouch.core.session.HttpFailure
import app.splouch.core.session.HttpResponse
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.MeetContext
import app.splouch.core.session.MeetSession
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.SplouchApi
import app.splouch.core.transport.SocketEvent
import app.splouch.core.transport.SplouchSocket
import app.splouch.core.transport.WebSocketTransport
import app.splouch.core.wire.ServerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Two checks against a real server, run only when `SPLOUCH_LIVE_SERVER=http://host:port`
 * is set. They use the JDK's own HTTP and WebSocket clients, so the core's contract
 * layer is exercised end to end without the app module or a device.
 */
class LiveServerTests {
    private val base: String? = System.getenv("SPLOUCH_LIVE_SERVER")?.takeIf { it.isNotBlank() }

    private class JdkClient : HttpClient, WebSocketTransport {
        private val client = java.net.http.HttpClient.newHttpClient()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            val req = HttpRequest.newBuilder(URI(url)).GET().apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            val r = try { client.send(req, BodyHandlers.ofString()) } catch (e: Exception) { throw HttpFailure(e.message ?: "io", e) }
            return HttpResponse(r.statusCode(), r.body(), r.headers().map().mapValues { it.value.joinToString(",") })
        }

        override fun connect(url: String, listener: WebSocketTransport.Listener): WebSocketTransport.Handle {
            var live: WebSocket? = null
            val fut: CompletableFuture<WebSocket> = client.newWebSocketBuilder().buildAsync(URI(url), object : WebSocket.Listener {
                private val buf = StringBuilder()
                // onOpen runs before the connect future completes, so the handle must hold the socket from here.
                override fun onOpen(ws: WebSocket) { live = ws; ws.request(1); listener.onOpen() }
                override fun onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    buf.append(data)
                    if (last) { listener.onMessage(buf.toString()); buf.setLength(0) }
                    ws.request(1)
                    return null
                }
                override fun onClose(ws: WebSocket, code: Int, reason: String): CompletionStage<*>? { listener.onClosed(); return null }
                override fun onError(ws: WebSocket, error: Throwable) = listener.onFailure(error)
            })
            fut.whenComplete { _, e -> if (e != null) listener.onFailure(e) }
            return object : WebSocketTransport.Handle {
                override fun send(text: String): Boolean = (live ?: fut.getNow(null))?.let { it.sendText(text, true); true } ?: false
                override fun close() { (live ?: fut.getNow(null))?.sendClose(WebSocket.NORMAL_CLOSURE, "") }
                override fun abort() { (live ?: fut.getNow(null))?.abort() }
            }
        }
    }

    @Test fun `handshake, config, schedule`() = runBlocking {
        val base = base ?: return@runBlocking
        val server = ServerAddress.parseOrNull(base) ?: error("bad SPLOUCH_LIVE_SERVER")
        val api = SplouchApi(JdkClient(), server)
        val info = assertIs<ApiResult.Ok<*>>(api.serverInfo()).value as app.splouch.core.wire.ServerInfo
        println("live: ${info.kind} '${info.name}' contract ${info.contract}")
        val meetId = if (info.kind == ServerKind.CLOUD) {
            val meets = assertIs<ApiResult.Ok<*>>(api.meets()).value as List<*>
            println("live: ${meets.size} meets")
            (meets.firstOrNull() as? app.splouch.core.wire.MeetSummary)?.id ?: return@runBlocking
        } else null
        val ctx = MeetContext(server, info.kind, meetId)
        val config = assertIs<ApiResult.Ok<*>>(api.meetConfig(ctx)).value as app.splouch.core.wire.MeetConfig
        println("live: meet '${config.title}' lanes=${config.settings.numLanes} locale=${config.settings.locale} labels=${config.settings.labels}")
        assertTrue(config.settings.numLanes in 1..12)
        val schedule = assertIs<ApiResult.Ok<*>>(api.schedule(ctx)).value as List<*>
        println("live: ${schedule.size} heats")
        val i18n = api.i18n("fr", null)
        assertIs<app.splouch.core.session.I18nResult.Ok>(i18n)
        assertEquals("fr", i18n.bundle.lang)
        assertIs<ApiResult.Ok<*>>(api.locales())
    }

    @Test fun `sockets connect, the connect burst arrives, a session joins`() = runBlocking {
        val base = base ?: return@runBlocking
        val server = ServerAddress.parseOrNull(base) ?: error("bad SPLOUCH_LIVE_SERVER")
        val client = JdkClient()
        val info = (SplouchApi(client, server).serverInfo() as ApiResult.Ok).value
        val meetId = if (info.kind == ServerKind.CLOUD) ((SplouchApi(client, server).meets() as ApiResult.Ok).value.firstOrNull()?.id ?: return@runBlocking) else null
        val ctx = MeetContext(server, info.kind, meetId)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

        val seen = ArrayList<String>()
        val bare = SplouchSocket(ctx.wsUrl("/ws/scoreboard"), client, scope)
        scope.launch { bare.events.collect { e -> synchronized(seen) { seen += when (e) { SocketEvent.Connected -> "connect"; SocketEvent.Disconnected -> "disconnect"; is SocketEvent.Message -> e.event } } } }
        scope.launch { bare.start(); if (ctx.joinsMeet) bare.send(app.splouch.core.wire.Frame.joinMeet(meetId!!, "live-test")) }
        withTimeout(10_000) { while (synchronized(seen) { "meet_live" !in seen }) delay(100) }
        println("live: socket events $seen")
        assertEquals("connect", seen.first())
        bare.close()

        val session = MeetSession(ctx, client, InMemoryVidStore(), scope, (SplouchApi(client, server).meetConfig(ctx) as ApiResult.Ok).value.settings.numLanes)
        scope.launch { session.start() }
        withTimeout(10_000) { while (!(session.scoreboardSocket.isConnected && session.resultsSocket.isConnected && session.scheduleSocket.isConnected)) delay(100) }
        delay(500)
        println("live: session live=${session.scoreboard.value.meetLive} event=${session.scoreboard.value.currentEvent} heat=${session.scoreboard.value.currentHeat} results waiting=${session.resultsView.value.waiting}")
        scope.launch { session.close() }
        delay(200)
    }
}
