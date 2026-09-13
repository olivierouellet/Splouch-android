package app.splouch.core

import app.splouch.core.session.ApiResult
import app.splouch.core.session.Contract
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.MeetContext
import app.splouch.core.session.MeetSession
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.SplouchApi
import app.splouch.core.support.FakeTransport
import app.splouch.core.support.StubHttp
import app.splouch.core.schedule.HeatRef
import app.splouch.core.wire.ContractVersions
import app.splouch.core.wire.Frame
import app.splouch.core.wire.ServerKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTests {
    @Test fun `server address normalises and keys the vid per origin`() {
        val a = ServerAddress.parseOrNull("HTTPS://Splouch.CA:443/")!!
        assertEquals("https://splouch.ca", a.origin)
        assertEquals("wss://splouch.ca/ws/results", a.wsUrl("/ws/results"))
        assertEquals("https://splouch.ca/server", a.httpUrl("/server"))
        assertEquals("splouch.ca", ServerAddress.parseOrNull("splouch.ca")!!.display)
        val pi = ServerAddress.parseOrNull("http://splouch.local:5000")!!
        assertEquals("http://splouch.local:5000", pi.origin)
        assertEquals("ws://splouch.local:5000/ws/scoreboard", pi.wsUrl("/ws/scoreboard"))
        assertEquals(ServerAddress.Result.CleartextNotLocal, ServerAddress.parse("http://192.168.1.10:5000"))
        assertEquals(ServerAddress.Result.CleartextNotLocal, ServerAddress.parse("http://splouch.ca"))
        assertEquals(ServerAddress.Result.Invalid, ServerAddress.parse("ftp://x"))
        assertEquals(ServerAddress.Result.Invalid, ServerAddress.parse(""))
        assertIs<ServerAddress.Result.Ok>(ServerAddress.parse("http://10.0.2.2:5000"))
        val vids = InMemoryVidStore()
        val v1 = vids.vid(a.origin)
        assertEquals(v1, vids.vid(ServerAddress.parseOrNull("https://splouch.ca/")!!.origin))
        assertNotEquals(v1, vids.vid(pi.origin))
        assertEquals(36, v1.length)
    }

    @Test fun `contract mismatch is a notice, never a refusal`() {
        assertNull(Contract.mismatchNotice(ContractVersions("v2", "v1")))
        val n = Contract.mismatchNotice(ContractVersions("v1", "v1"))!!
        assertTrue(n.contains("v1") && n.contains("v2"))
    }

    @Test fun `built contract versions match the documents' headers`() {
        val docs = File("../../Splouch/docs")
        if (!docs.isDirectory) return
        fun header(name: String) = Regex("""\*\*Contract version: `(v\d+)`\*\*""").find(File(docs, name).readText())?.groupValues?.get(1)
        assertEquals(Contract.BUILT.api, header("api.md"))
        assertEquals(Contract.BUILT.app, header("app.md"))
    }

    @Test fun `endpoints resolve per server kind`() {
        val s = ServerAddress.parseOrNull("https://c.example")!!
        val cloud = MeetContext(s, ServerKind.CLOUD, "ab c")
        assertEquals("https://c.example/meet/ab%20c/config", cloud.configUrl)
        assertEquals("https://c.example/meet/ab%20c/schedule", cloud.scheduleUrl)
        assertTrue(cloud.joinsMeet)
        val pi = MeetContext(ServerAddress.parseOrNull("http://pi.local:5000")!!, ServerKind.PI, null)
        assertEquals("http://pi.local:5000/config", pi.configUrl)
        assertEquals("http://pi.local:5000/schedule.json", pi.scheduleUrl)
        assertFalse(pi.joinsMeet)
    }

    @Test fun `api - handshake, 404 is gone, anything else a fault, i18n revalidates`() = runTest {
        val http = StubHttp()
        val s = ServerAddress.parseOrNull("https://c.example")!!
        val api = SplouchApi(http, s)
        http.on("https://c.example/server", body = """{"kind":"cloud","name":"Splouch","contract":{"api":"v2","app":"v1"}}""")
        assertEquals(ServerKind.CLOUD, (api.serverInfo() as ApiResult.Ok).value.kind)
        http.on("https://c.example/meet/gone/config", status = 404, body = "{}")
        assertEquals(ApiResult.NotFound, api.meetConfig(MeetContext(s, ServerKind.CLOUD, "gone")))
        http.on("https://c.example/meet/bad/config", status = 500, body = "")
        assertIs<ApiResult.Failure>(api.meetConfig(MeetContext(s, ServerKind.CLOUD, "bad")))
        assertIs<ApiResult.Failure>(api.meetConfig(MeetContext(s, ServerKind.CLOUD, "unrouted")))
        http.on("https://c.example/i18n/fr", body = """{"lang":"fr","mobile":{}}""", headers = mapOf("ETag" to "\"abc\""))
        val r = api.i18n("fr", null)
        assertIs<app.splouch.core.session.I18nResult.Ok>(r)
        assertEquals("\"abc\"", r.etag)
        http.on("https://c.example/i18n/fr", status = 304)
        assertEquals(app.splouch.core.session.I18nResult.NotModified, api.i18n("fr", "\"abc\""))
        assertEquals("\"abc\"", http.calls.last().second["If-None-Match"])
    }

    @Test fun `a cloud session joins on every connect of every socket with this server's vid`() = runTest {
        val transport = FakeTransport()
        val vids = InMemoryVidStore()
        val ctx = MeetContext(ServerAddress.parseOrNull("https://c.example")!!, ServerKind.CLOUD, "m1")
        val session = MeetSession(ctx, transport, vids, backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        session.start()
        assertEquals(listOf("wss://c.example/ws/scoreboard", "wss://c.example/ws/results", "wss://c.example/ws/schedule"), transport.connections.map { it.url })
        transport.connections.forEach { it.serverOpen() }
        runCurrent()
        val vid = vids.vid("https://c.example")
        transport.connections.forEach { c ->
            val join = Frame.decode(c.sent.single())!!
            assertEquals("join_meet", join.event)
            assertEquals("m1", join.data!!.jsonObject["meet_id"]!!.jsonPrimitive.content)
            assertEquals(vid, join.data!!.jsonObject["vid"]!!.jsonPrimitive.content)
        }
        // reconnect → join again, and the owner is told to re-check the meet (A-09)
        val reconnects = ArrayList<Unit>()
        backgroundScope.launch { session.reconnects.toList(reconnects) }
        transport.connections[0].serverClose(); runCurrent()
        advanceTimeBy(500); runCurrent()
        transport.last.serverOpen(); runCurrent()
        assertEquals("join_meet", transport.last.sentEvents().single())
        assertEquals(1, reconnects.size)
        session.close()
    }

    @Test fun `a pi session never sends join_meet and mints no vid`() = runTest {
        val transport = FakeTransport()
        val vids = InMemoryVidStore()
        val ctx = MeetContext(ServerAddress.parseOrNull("http://pi.local:5000")!!, ServerKind.PI, null)
        val session = MeetSession(ctx, transport, vids, backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        session.start()
        transport.connections.forEach { it.serverOpen() }
        runCurrent()
        assertTrue(transport.connections.all { it.sent.isEmpty() })
        session.close()
    }

    @Test fun `frames feed the boards, current heat comes off both sockets, a drop wipes results and stops the clock`() = runTest {
        val transport = FakeTransport()
        val ctx = MeetContext(ServerAddress.parseOrNull("https://c.example")!!, ServerKind.CLOUD, "m1")
        val session = MeetSession(ctx, transport, InMemoryVidStore(), backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        session.start()
        val sb = transport.connections[0]; val rs = transport.connections[1]; val sc = transport.connections[2]
        listOf(sb, rs, sc).forEach { it.serverOpen() }
        runCurrent()
        sb.serverSend("""{"event":"meet_live","data":{"live":true}}""")
        sb.serverSend("""{"event":"update_scoreboard","data":{"current_event":"3","current_heat":"1","lane_running1":true,"running_time":"10.00"}}""")
        runCurrent()
        assertEquals(HeatRef("3", "1"), session.currentHeat.value)
        assertEquals("10.0", session.scoreboard.value.lanes[0].time)
        session.startTicker()
        advanceTimeBy(1_050)
        assertEquals("11.0", session.scoreboard.value.lanes[0].time)
        rs.serverSend("""{"event":"results_snapshot","data":{"event":"2","heat":"9","lanes":[{"channel":1,"place":"1","time":"1.00","name":"A"}]}}""")
        runCurrent()
        assertEquals(HeatRef("2", "9"), session.currentHeat.value)
        assertFalse(session.resultsView.value.waiting)
        val updates = ArrayList<Unit>()
        backgroundScope.launch { session.scheduleUpdates.toList(updates) }
        sc.serverSend("""{"event":"schedule_update"}"""); runCurrent()
        assertEquals(1, updates.size)
        val reloads = ArrayList<Unit>()
        backgroundScope.launch { session.reloads.toList(reloads) }
        sb.serverSend("""{"event":"reload","data":{}}"""); runCurrent()
        assertEquals(1, reloads.size)
        // background: ticker stops, base forgotten, digits held
        session.background()
        advanceTimeBy(5_000)
        assertEquals("11.0", session.scoreboard.value.lanes[0].time)
        assertFalse(session.scoreboard.value.clockRunning)
        // results socket drops → board wiped
        rs.serverClose(); runCurrent()
        assertTrue(session.resultsView.value.waiting)
        // scoreboard socket drops → not live
        sb.serverClose(); runCurrent()
        assertFalse(session.scoreboard.value.meetLive)
        session.close()
    }
}
