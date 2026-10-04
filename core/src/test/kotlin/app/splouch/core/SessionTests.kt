package app.splouch.core

import app.splouch.core.board.LapSettings
import app.splouch.core.schedule.HeatRef
import app.splouch.core.session.ApiResult
import app.splouch.core.session.Contract
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.MeetBase
import app.splouch.core.session.MeetContext
import app.splouch.core.session.MeetSession
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.SplouchApi
import app.splouch.core.support.FakeTransport
import app.splouch.core.support.StubHttp
import app.splouch.core.wire.ContractVersions
import app.splouch.core.wire.Frame
import app.splouch.core.wire.ServerKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SessionTests {
    @Test fun `plain http is allowed on the local network and nowhere else`() {
        listOf(
            "192.168.1.20", "10.255.0.1", "172.31.255.254", "169.254.3.4", "127.4.5.6", "10.0.2.2",
            "localhost", "splouch.local", "[::1]", "[fd12::1]", "[fe80::1%wlan0]", "[::ffff:192.168.1.2]",
        ).forEach { assertIs<ServerAddress.Result.Ok>(ServerAddress.parse("http://$it:5000"), it) }
        listOf(
            "203.0.113.5", "8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.1.1", "[2001:db8::1]",
            "[::ffff:8.8.8.8]", "localhost.example", "10.0.0.1.example", "192.168.1.20.example",
            "010.0.0.1", "3232235796", "local", "[::]",
        ).forEach { assertEquals(ServerAddress.Result.CleartextNotLocal, ServerAddress.parse("http://$it:5000"), it) }
        // The transport sees hosts as OkHttp gives them: IPv6 without brackets.
        listOf("fd12::1", "fe80::1", "::1", "::ffff:c0a8:102", "192.168.1.20")
            .forEach { assertTrue(ServerAddress.isLocalName(it), it) }
        listOf("2001:db8::1", "::ffff:808:808", "203.0.113.5", "1:2:3:4:5:6:7:8:9", "fd12:::1")
            .forEach { assertFalse(ServerAddress.isLocalName(it), it) }
        // Over https the range does not matter.
        assertIs<ServerAddress.Result.Ok>(ServerAddress.parse("https://203.0.113.5"))
    }

    @Test fun `server address normalises and keys the vid per origin`() {
        val a = ServerAddress.parseOrNull("HTTPS://Splouch.CA:443/")!!
        assertEquals("https://splouch.ca", a.origin)
        assertEquals("wss://splouch.ca/ws/results", a.wsUrl("/ws/results"))
        assertEquals("https://splouch.ca/server", a.httpUrl("/server"))
        assertEquals("splouch.ca", ServerAddress.parseOrNull("splouch.ca")!!.display)
        val pi = ServerAddress.parseOrNull("http://splouch.local:5000")!!
        assertEquals("http://splouch.local:5000", pi.origin)
        assertEquals("ws://splouch.local:5000/ws/scoreboard", pi.wsUrl("/ws/scoreboard"))
        assertEquals(ServerAddress.Result.CleartextNotLocal, ServerAddress.parse("http://203.0.113.10:5000"))
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
        assertNull(Contract.mismatchNotice(ContractVersions("v2", "v3")))
        val n = Contract.mismatchNotice(ContractVersions("v1", "v1"))!!
        assertTrue(n.contains("v1") && n.contains("v2"))
        // P-14 against app.md v3: a v2 server is named beside the v3 this app was built for.
        val v2 = Contract.mismatchNotice(ContractVersions("v2", "v2"))!!
        assertTrue(v2.contains("app v2") && v2.contains("app v3"))
    }

    @Test fun `built contract versions match the documents' headers`() {
        val docs = File("../../Splouch/docs")
        if (!docs.isDirectory) return
        fun header(name: String) =
            Regex("""\*\*Contract version: `(v\d+)`\*\*""").find(File(docs, name).readText())?.groupValues?.get(1)
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
        http.on(
            "https://c.example/server",
            body = """{"kind":"cloud","name":"Splouch","contract":{"api":"v2","app":"v2"}}""",
        )
        assertEquals(ServerKind.CLOUD, (api.serverInfo() as ApiResult.Ok).value.kind)
        http.on("https://c.example/meet/gone/config", status = 404, body = "{}")
        assertEquals(ApiResult.NotFound, api.meetConfig(MeetContext(s, ServerKind.CLOUD, "gone")))
        http.on("https://c.example/meet/bad/config", status = 500, body = "")
        assertIs<ApiResult.Failure>(api.meetConfig(MeetContext(s, ServerKind.CLOUD, "bad")))
        assertIs<ApiResult.Failure>(api.meetConfig(MeetContext(s, ServerKind.CLOUD, "unrouted")))
        http.on(
            "https://c.example/i18n/fr",
            body = """{"lang":"fr","mobile":{}}""",
            headers = mapOf("ETag" to "\"abc\""),
        )
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
        val session =
            MeetSession(ctx, transport, vids, backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        session.start()
        assertEquals(
            listOf("wss://c.example/ws/scoreboard", "wss://c.example/ws/results", "wss://c.example/ws/schedule"),
            transport.connections.map {
                it.url
            },
        )
        transport.connections.forEach { it.serverOpen() }
        runCurrent()
        val vid = vids.vid("https://c.example")
        transport.connections.forEach { c ->
            val join = Frame.decode(c.sent.single())!!
            assertEquals("join_meet", join.event)
            assertEquals("m1", join.data!!.jsonObject["meet_id"]!!.jsonPrimitive.content)
            assertEquals(vid, join.data.jsonObject["vid"]!!.jsonPrimitive.content)
        }
        // reconnect → join again, and the owner is told to re-check the meet (A-09)
        val reconnects = ArrayList<Unit>()
        backgroundScope.launch { session.reconnects.toList(reconnects) }
        transport.connections[0].serverClose()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        transport.last.serverOpen()
        runCurrent()
        assertEquals("join_meet", transport.last.sentEvents().single())
        assertEquals(1, reconnects.size)
        session.close()
    }

    @Test fun `a pi session never sends join_meet and mints no vid`() = runTest {
        val transport = FakeTransport()
        val vids = InMemoryVidStore()
        val ctx = MeetContext(ServerAddress.parseOrNull("http://pi.local:5000")!!, ServerKind.PI, null)
        val session =
            MeetSession(ctx, transport, vids, backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        session.start()
        transport.connections.forEach { it.serverOpen() }
        runCurrent()
        assertTrue(transport.connections.all { it.sent.isEmpty() })
        session.close()
    }

    @Test fun `frames feed the boards, current heat comes off both sockets, a drop wipes results and stops the clock`() =
        runTest {
            val transport = FakeTransport()
            val ctx = MeetContext(ServerAddress.parseOrNull("https://c.example")!!, ServerKind.CLOUD, "m1")
            val session =
                MeetSession(
                    ctx,
                    transport,
                    InMemoryVidStore(),
                    backgroundScope,
                    numLanes = 4,
                    timeSource = testScheduler.timeSource,
                )
            session.start()
            val sb = transport.connections[0]
            val rs = transport.connections[1]
            val sc = transport.connections[2]
            listOf(sb, rs, sc).forEach { it.serverOpen() }
            runCurrent()
            sb.serverSend("""{"event":"meet_live","data":{"live":true}}""")
            sb.serverSend(
                """{"event":"update_scoreboard","data":{"current_event":"3","current_heat":"1","lane_running1":true,"running_time":"10.00"}}""",
            )
            runCurrent()
            assertEquals(HeatRef("3", "1"), session.currentHeat.value)
            assertEquals("10.0", session.scoreboard.value.lanes[0].time)
            session.startTicker()
            advanceTimeBy(1_050)
            assertEquals("11.0", session.scoreboard.value.lanes[0].time)
            rs.serverSend(
                """{"event":"results_snapshot","data":{"event":"2","heat":"9","lanes":[{"channel":1,"place":"1","time":"1.00","name":"A"}]}}""",
            )
            runCurrent()
            assertEquals(HeatRef("2", "9"), session.currentHeat.value)
            assertFalse(session.resultsView.value.waiting)
            val updates = ArrayList<Unit>()
            backgroundScope.launch { session.scheduleUpdates.toList(updates) }
            sc.serverSend("""{"event":"schedule_update"}""")
            runCurrent()
            assertEquals(1, updates.size)
            val reloads = ArrayList<Unit>()
            backgroundScope.launch { session.reloads.toList(reloads) }
            sb.serverSend("""{"event":"reload","data":{}}""")
            runCurrent()
            assertEquals(1, reloads.size)
            // background: ticker stops, base forgotten, digits held
            session.background()
            advanceTimeBy(5_000)
            assertEquals("11.0", session.scoreboard.value.lanes[0].time)
            assertFalse(session.scoreboard.value.clockRunning)
            // results socket drops → board wiped
            rs.serverClose()
            runCurrent()
            assertTrue(session.resultsView.value.waiting)
            // scoreboard socket drops → not live
            sb.serverClose()
            runCurrent()
            assertFalse(session.scoreboard.value.meetLive)
            session.close()
        }

    @Test fun `a reset takes the whole board down, lap count included (api 2_2)`() = runTest {
        val transport = FakeTransport()
        val ctx = MeetContext(ServerAddress.parseOrNull("http://pi.local:5000")!!, ServerKind.PI, null)
        val session =
            MeetSession(
                ctx,
                transport,
                InMemoryVidStore(),
                backgroundScope,
                numLanes = 4,
                timeSource = testScheduler.timeSource,
            )
        session.start()
        val sb = transport.connections[0]
        sb.serverOpen()
        runCurrent()
        sb.serverSend(
            """{"event":"update_scoreboard","data":{"current_event":"3","current_heat":"1","expected_splits":8,"split_step":2,"lane_name1":"Ann","lane_splits1":6,"lane_time1":"1:02.50"}}""",
        )
        runCurrent()
        val laps = LapSettings(show = true)
        assertEquals("6", session.scoreboard.value.let { it.lap(it.lanes[0], laps) }?.text)
        assertEquals(HeatRef("3", "1"), session.currentHeat.value)

        // The test session is over and the operator's own meet has been reloaded: the board
        // repaints from the real meet, and nothing from the recording stays on screen.
        sb.serverSend("""{"event":"reset","data":{}}""")
        runCurrent()
        assertNull(session.scoreboard.value.let { it.lap(it.lanes[0], laps) })
        assertEquals("", session.scoreboard.value.lanes[0].time)
        assertEquals("", session.scoreboard.value.currentEvent)
        assertNull(session.currentHeat.value)
        session.close()
    }

    // ── app.md v3: C-11, C-12 ─────────────────────────────────────────────────

    @Test fun `C-11 a base parses with its path, and is held to the cleartext floor`() {
        assertEquals("https://ca1.example/w2", MeetBase.parse(" https://ca1.example/w2/ ")!!.url)
        assertEquals("https://ca1.example", MeetBase.parse("https://CA1.example:443")!!.url)
        assertEquals("http://127.0.0.1:5055/w1", MeetBase.parse("http://127.0.0.1:5055/w1")!!.url)
        assertEquals(
            MeetBase.of(ServerAddress.parseOrNull("https://c.example")!!),
            MeetBase.parse("https://c.example/"),
        )
        assertNull(MeetBase.parse(null))
        assertNull(MeetBase.parse("  "))
        assertNull(MeetBase.parse("http://ca1.example/w2")) // cleartext to a public host
        assertNull(MeetBase.parse("ca1.example/w2")) // no scheme
        assertNull(MeetBase.parse("ftp://ca1.example"))
        assertNull(MeetBase.parse("https://u:p@ca1.example/w2"))
        assertNull(MeetBase.parse("https://ca1.example/w2?x=1"))
    }

    @Test fun `C-11 a meet's sockets, config, schedule and icon are at its base, the server URL without one`() = runTest {
        val server = ServerAddress.parseOrNull("https://c.example")!!
        val ctx = MeetContext(server, ServerKind.CLOUD, "m 1", MeetBase.parse("https://ca1.example/w2")!!)
        assertEquals("https://ca1.example/w2/meet/m%201/config", ctx.configUrl)
        assertEquals("https://ca1.example/w2/meet/m%201/schedule", ctx.scheduleUrl)
        assertEquals("https://ca1.example/w2/icon/m%201", ctx.iconUrl)
        // strings stay on the server the list came from
        assertEquals("https://c.example/i18n/fr", ctx.i18nUrl("fr"))
        // no base (a server older than v3): the server URL, as before
        val plain = MeetContext(server, ServerKind.CLOUD, "m1")
        assertEquals("https://c.example/meet/m1/config", plain.configUrl)
        assertEquals("https://c.example/meet/m1/schedule", plain.scheduleUrl)
        assertEquals("https://c.example/icon/m1", plain.iconUrl)
        assertNull(MeetContext(ServerAddress.parseOrNull("http://pi.local:5000")!!, ServerKind.PI, null).iconUrl)

        val transport = FakeTransport()
        val vids = InMemoryVidStore()
        val session =
            MeetSession(ctx, transport, vids, backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        session.start()
        assertEquals(
            listOf(
                "wss://ca1.example/w2/ws/scoreboard",
                "wss://ca1.example/w2/ws/results",
                "wss://ca1.example/w2/ws/schedule",
            ),
            transport.connections.map { it.url },
        )
        transport.connections.forEach { it.serverOpen() }
        runCurrent()
        // C-10: the vid is the list server's, never one minted for the worker's host
        val vid = vids.vid("https://c.example")
        transport.connections.forEach { c ->
            assertEquals(vid, Frame.decode(c.sent.single())!!.data!!.jsonObject["vid"]!!.jsonPrimitive.content)
        }
        session.close()
    }

    @Test fun `C-12 a moved on any socket switches all three to the new base, holds the board, and joins there`() = runTest {
        val transport = FakeTransport()
        val vids = InMemoryVidStore()
        val server = ServerAddress.parseOrNull("https://c.example")!!
        val ctx = MeetContext(server, ServerKind.CLOUD, "m1", MeetBase.parse("https://ca1.example/w1")!!)
        val session =
            MeetSession(ctx, transport, vids, backgroundScope, numLanes = 4, timeSource = testScheduler.timeSource)
        val moves = ArrayList<MeetBase>()
        backgroundScope.launch { session.moves.toList(moves) }
        session.start()
        transport.connections.forEach { it.serverOpen() }
        runCurrent()
        transport.connections[0].serverSend("""{"event":"meet_live","data":{"live":true}}""")
        runCurrent()
        assertTrue(session.scoreboard.value.meetLive)

        // heard on the schedule socket: all three go
        val old = transport.connections.toList()
        old[2].serverSend(
            """{"event":"moved","data":{"url":"https://ca2.example/w3/mobile?meet=m1","base":"https://ca2.example/w3"}}""",
        )
        runCurrent()
        val moved = MeetBase.parse("https://ca2.example/w3")!!
        assertTrue(old.all { it.closed })
        assertEquals(moved, session.context.base)
        assertEquals(server, session.context.server)
        assertEquals(listOf(moved), moves)
        assertEquals(
            listOf(
                "wss://ca2.example/w3/ws/scoreboard",
                "wss://ca2.example/w3/ws/results",
                "wss://ca2.example/w3/ws/schedule",
            ),
            transport.connections.drop(3).map { it.url },
        )
        // held until the new worker replays it — never "gone", never left ticking as live
        assertFalse(session.scoreboard.value.meetLive)
        transport.connections.drop(3).forEach { it.serverOpen() }
        runCurrent()
        transport.connections.drop(3).forEach { c ->
            val join = Frame.decode(c.sent.single())!!
            assertEquals("join_meet", join.event)
            assertEquals(vids.vid("https://c.example"), join.data!!.jsonObject["vid"]!!.jsonPrimitive.content)
        }

        // the same base again, or one that cannot be followed, moves nothing
        val now = transport.connections.size
        transport.last.serverSend("""{"event":"moved","data":{"url":"","base":"https://ca2.example/w3/"}}""")
        transport.last.serverSend("""{"event":"moved","data":{"url":"","base":"http://evil.example/w1"}}""")
        transport.last.serverSend("""{"event":"moved","data":{}}""")
        runCurrent()
        assertEquals(now, transport.connections.size)
        assertEquals(1, moves.size)
        session.close()
    }
}
