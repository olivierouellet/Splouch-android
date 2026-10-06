package app.splouch.core

import app.splouch.core.session.AddServerResult
import app.splouch.core.session.AppModel
import app.splouch.core.session.InMemoryPreferencesStore
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.InviteFailure
import app.splouch.core.session.KnownServer
import app.splouch.core.session.LocalSearch
import app.splouch.core.session.MeetBase
import app.splouch.core.session.MeetTab
import app.splouch.core.session.Preferences
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.ServerBrowser
import app.splouch.core.session.ServerInvite
import app.splouch.core.session.ServerLink
import app.splouch.core.strings.InMemoryBundleCache
import app.splouch.core.strings.Labels
import app.splouch.core.support.FakeTransport
import app.splouch.core.support.StubHttp
import app.splouch.core.wire.ServerKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AppModelTests {
    private val cloud = "https://c.example"
    private val pi = "http://pi.local:5000"

    private class Rig(
        scope: TestScope,
        prefs: Preferences = Preferences(),
        val vids: InMemoryVidStore = InMemoryVidStore(),
    ) {
        val http = StubHttp()
        val transport = FakeTransport()
        val prefsStore = InMemoryPreferencesStore(prefs)
        val model = AppModel(
            ServerAddress.parseOrNull("https://c.example")!!, http, transport, vids, prefsStore,
            InMemoryBundleCache(), scope.backgroundScope, deviceLang = "fr",
            timeSource = scope.testScheduler.timeSource,
        )
    }

    // Each payload stays on one line, as the server sends it, so a fixture reads against the wire.
    @Suppress("ktlint:standard:max-line-length")
    private fun StubHttp.cloudRoutes(base: String = "https://c.example") {
        on("$base/server", body = """{"kind":"cloud","name":"Cloud","contract":{"api":"v2","app":"v3"}}""")
        on(
            "$base/meets",
            body = """{"meets":[{"id":"m1","name":"Meet One","offline":false},{"id":"m2","name":"Old","offline":true}]}""",
        )
        on(
            "$base/picker/config?lang=fr",
            body = """{"title":"Splouch","lang":"fr","analytics_enabled":true,"strings":{"results_disclaimer":"D","privacy_note":"P"}}""",
        )
        on(
            "$base/servers",
            body = """{"servers":[{"name":"Cloud","url":"$base","kind":"cloud"},{"name":"Club X","url":"https://x.example","kind":"cloud"}]}""",
        )
        on("$base/locales", body = """[{"code":"en","name":"English"},{"code":"fr","name":"Français"}]""")
        on(
            "$base/i18n/fr",
            body = """{"lang":"fr","mobile":{"scoreboard":"Tableau"},"labels":{"short":{"event":"ÉP","heat":"SÉR"},"long":{"event":"ÉPREUVE","heat":"SÉRIE"}}}""",
            headers = mapOf("ETag" to "\"e1\""),
        )
        on(
            "$base/i18n/en",
            body = """{"lang":"en","mobile":{"scoreboard":"Scoreboard"},"labels":{"short":{"event":"EV","heat":"HT"},"long":{"event":"EVENT","heat":"HEAT"}}}""",
        )
        on(
            "$base/meet/m1/config",
            body = """{"name":"Meet One","live":true,"settings":{"num_lanes":6,"locale":"en","labels":{"event":"EV","heat":"HT"},"label_style":"short"}}""",
        )
        on("$base/meet/m1/schedule", body = """{"heats":[]}""")
    }

    @Test fun `a cloud server loads the picker, its directory and the strings`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        val s = r.model.current
        assertEquals(ServerKind.CLOUD, s.kind)
        assertNull(s.contractNotice)
        assertEquals(listOf("m1", "m2"), s.picker.meets.map { it.id })
        assertTrue(s.picker.loaded)
        assertEquals("D", s.picker.config!!.strings["results_disclaimer"])
        assertEquals(listOf("c.example", "x.example"), s.servers.map { it.address.display })
        assertEquals("Cloud", s.servers[0].name)
        assertEquals("Tableau", s.pickerStrings.mobile("scoreboard"))
        assertEquals(2, s.locales.size)
        assertNull(s.meet)
    }

    @Test fun `the directory lists plain http servers only on the local network`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.on(
            "$cloud/servers",
            body = """{"servers":[{"name":"Pool Pi","url":"http://192.168.1.20:5000","kind":"pi"},""" +
                """{"name":"Stranger","url":"http://203.0.113.5:5000","kind":"pi"}]}""",
        )
        r.model.start()
        runCurrent()
        assertEquals(listOf("c.example", "192.168.1.20:5000"), r.model.current.servers.map { it.address.display })
    }

    @Test fun `opening a meet starts a session in the meet's language with long headers over the operator's labels`() =
        runTest {
            val r = Rig(this)
            r.http.cloudRoutes()
            r.model.start()
            runCurrent()
            r.model.openMeet("m1")
            runCurrent()
            val m = r.model.current.meet!!
            assertEquals("m1", m.context.meetId)
            assertEquals(6, m.config.settings.numLanes)
            assertEquals("en", m.lang)
            assertEquals("Scoreboard", m.strings.mobile("scoreboard"))
            // The operator sent short words and label_style "short"; T-09 starts from long anyway.
            assertEquals(mapOf("event" to "EVENT", "heat" to "HEAT"), m.labels)
            assertEquals(emptyList(), m.schedule)
            assertEquals(3, r.transport.connections.size)
            // a language choice re-derives strings and labels from the server's table
            r.model.setLang("fr")
            runCurrent()
            val m2 = r.model.current.meet!!
            assertEquals("fr", m2.lang)
            assertEquals("Tableau", m2.strings.mobile("scoreboard"))
            assertEquals("ÉPREUVE", m2.labels["event"])
            // T-09 is withdrawn, not deleted: the choice is still stored, and still read
            // over by `effectiveLabelStyle`, so the header stays long while the control is away.
            r.model.setLabelStyle("short")
            runCurrent()
            assertEquals("short", r.prefsStore.load().labelStyle)
            assertEquals(Labels.LONG, r.prefsStore.load().effectiveLabelStyle)
            assertEquals("ÉPREUVE", r.model.current.meet!!.labels["event"])
            r.model.closeMeet()
            assertNull(r.model.current.meet)
            assertTrue(r.transport.connections.all { it.closed })
        }

    @Test fun `a 404 on the config re-fetch means the meet is gone - back to the picker`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        r.model.openMeet("m1")
        runCurrent()
        assertNotNull(r.model.current.meet)
        r.http.on("https://c.example/meet/m1/config", status = 404, body = "{}")
        r.model.foreground()
        runCurrent()
        assertNull(r.model.current.meet)
        assertTrue(r.model.current.meetGone)
        // a network fault on the same call is not a gone meet
        r.model.dismissMeetGone()
        r.http.cloudRoutes()
        r.model.openMeet("m1")
        runCurrent()
        r.http.on("https://c.example/meet/m1/config", status = 503, body = "")
        r.model.foreground()
        runCurrent()
        assertNotNull(r.model.current.meet)
        assertFalse(r.model.current.meetGone)
    }

    @Test fun `A-11 the Results tab comes and goes with the console, on the re-fetch the app already makes`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        r.model.openMeet("m1")
        runCurrent()
        fun tabs() = MeetTab.of(r.model.current.meet!!.config)
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.RESULTS, MeetTab.SCHEDULE), tabs())

        // The operator unplugs the console mid-meet and drives the boards from /manual.
        // The Pi re-registers, the cloud's config changes, and the app finds out on the
        // fetch it was already going to make — here, coming back to the foreground.
        fun console(json: String) = r.http.on(
            "https://c.example/meet/m1/config",
            body = """{"name":"Meet One","live":true,"settings":{"num_lanes":6,"locale":"en","console":$json}}""",
        )
        console("""{"key":"manual","timed":false}""")
        r.model.foreground()
        runCurrent()
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.SCHEDULE), tabs())
        // The meet itself is untouched: same session, same sockets, nothing re-opened.
        assertEquals(3, r.transport.connections.count { !it.closed })

        // Pull-to-refresh (A-05) is the same fetch and reads the same flag.
        console("""{"key":"cts_gen6","timed":true}""")
        r.model.refreshMeet()
        runCurrent()
        assertEquals(3, tabs().size)
        assertFalse(r.model.current.meet!!.refreshing)

        // A network fault changes nothing — least of all takes a tab away.
        r.http.on("https://c.example/meet/m1/config", status = 503, body = "")
        r.model.refreshMeet()
        runCurrent()
        assertEquals(3, tabs().size)
    }

    @Test fun `a pi goes straight to the board, joins nothing, and shows a contract notice once`() = runTest {
        val r = Rig(this, Preferences(server = pi))
        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v1","app":"v2"}}""")
        r.http.on(
            "$pi/config",
            body = """{"meet_title":"Pool Meet","num_lanes":8,"locale":"fr","labels":{"event":"ÉP"}}""",
        )
        r.http.on("$pi/schedule.json", body = """{"heats":[]}""")
        r.model.start()
        runCurrent()
        val s = r.model.current
        assertEquals(ServerKind.PI, s.kind)
        assertFalse(s.isDefaultServer)
        assertNotNull(s.contractNotice)
        val m = s.meet!!
        assertNull(m.context.meetId)
        assertEquals("Pool Meet", m.config.title)
        assertEquals("fr", m.lang)
        r.transport.connections.forEach { it.serverOpen() }
        runCurrent()
        assertTrue(r.transport.connections.all { it.sent.isEmpty() })
        r.model.dismissNotice()
        assertNull(r.model.current.contractNotice)
    }

    private class FakeBrowser : ServerBrowser {
        var running = false
        var starts = 0
        override fun start() {
            running = true
            starts++
        }
        override fun stop() {
            running = false
        }
    }

    private fun found(origin: String, name: String) =
        KnownServer(ServerAddress.parseOrNull(origin)!!, name, ServerKind.PI, KnownServer.Source.DISCOVERED)

    @Test fun `the local browse waits for a tap and ends after ten seconds`() = runTest {
        val r = Rig(this)
        val browser = FakeBrowser()
        r.model.serverBrowser = browser
        r.http.cloudRoutes()
        r.model.start()
        r.model.foreground()
        runCurrent()
        assertEquals(0, browser.starts)
        assertEquals(LocalSearch.IDLE, r.model.current.localSearch)

        r.model.searchLocal()
        assertTrue(browser.running)
        assertEquals(LocalSearch.SEARCHING, r.model.current.localSearch)
        advanceTimeBy(AppModel.LOCAL_SEARCH_LIMIT - 1.milliseconds)
        assertTrue(browser.running)
        advanceTimeBy(2.milliseconds)
        assertFalse(browser.running)
        assertEquals(LocalSearch.DONE, r.model.current.localSearch)

        // Search again is a fresh browse with a fresh limit.
        r.model.searchLocal()
        assertEquals(2, browser.starts)
        assertEquals(LocalSearch.SEARCHING, r.model.current.localSearch)
        r.model.background()
        assertFalse(browser.running)
        assertEquals(LocalSearch.IDLE, r.model.current.localSearch)
    }

    @Test fun `a Pi found outlives the scan until the sheet closes, and stays listed once picked`() = runTest {
        val r = Rig(this)
        val browser = FakeBrowser()
        r.model.serverBrowser = browser
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        // A resolve landing outside a browse is somebody else's late answer.
        r.model.setDiscovered(listOf(found(pi, "Piscine")))
        assertTrue(r.model.current.servers.none { it.source == KnownServer.Source.DISCOVERED })

        r.model.searchLocal()
        r.model.setDiscovered(listOf(found(pi, "Piscine")))
        advanceTimeBy(AppModel.LOCAL_SEARCH_LIMIT + 1.milliseconds)
        // A scan, not a watch: the browse stops whatever it found, and the find stays on offer.
        assertFalse(browser.running)
        assertEquals(LocalSearch.DONE, r.model.current.localSearch)
        assertEquals(KnownServer.Source.DISCOVERED, r.model.current.servers.single { it.name == "Piscine" }.source)
        // ...and a late resolve after the scan is dropped.
        r.model.setDiscovered(emptyList())
        assertEquals(1, r.model.current.servers.count { it.source == KnownServer.Source.DISCOVERED })

        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v2","app":"v2"}}""")
        r.http.on("$pi/config", body = """{"meet_title":"Pool","num_lanes":8}""")
        r.http.on("$pi/schedule.json", body = """{"heats":[]}""")
        r.model.selectServer(ServerAddress.parseOrNull(pi)!!)
        r.model.stopLocalSearch()
        runCurrent()
        assertFalse(browser.running)
        assertEquals(LocalSearch.IDLE, r.model.current.localSearch)
        // Picked, not saved — yet still in the list, or the sheet would show nothing selected.
        assertEquals(emptyList(), r.prefsStore.load().servers)
        val listed = r.model.current.servers.single { it.address.origin == ServerAddress.parseOrNull(pi)!!.origin }
        assertEquals(KnownServer.Source.CURRENT, listed.source)
        assertEquals("Piscine", listed.name)
    }

    @Test fun `adding a server checks it first and only then saves and selects it`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        assertEquals(AddServerResult.InvalidAddress, r.model.addServer("not a url at all ://"))
        assertEquals(AddServerResult.CleartextNotLocal, r.model.addServer("http://203.0.113.10:5000"))
        assertIs<AddServerResult.Unreachable>(r.model.addServer("https://nowhere.example"))
        assertEquals(emptyList(), r.prefsStore.load().servers)
        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v2","app":"v2"}}""")
        r.http.on("$pi/config", body = """{"meet_title":"Pool","num_lanes":8}""")
        r.http.on("$pi/schedule.json", body = """{"heats":[]}""")
        val ok = r.model.addServer(pi)
        assertIs<AddServerResult.Ok>(ok)
        assertEquals("Piscine", ok.server.name)
        runCurrent()
        assertEquals(listOf(pi), r.prefsStore.load().servers)
        assertEquals(pi, r.prefsStore.load().server)
        assertEquals(ServerKind.PI, r.model.current.kind)
        assertNotNull(r.model.current.meet)
        // P-13: removing is a one-finger swipe in the UI, so it hands back what it took.
        val removed = r.model.removeServer(ServerAddress.parseOrNull(pi)!!)
        runCurrent()
        assertNotNull(removed)
        assertEquals(0, removed.index)
        assertTrue(removed.wasSelected)
        assertTrue(r.model.current.isDefaultServer)
        assertNull(r.prefsStore.load().server)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // ...and undo puts it back where it was, and back in use, without asking the
        // network again — the handshake already happened when it was added.
        r.model.restoreServer(removed)
        runCurrent()
        assertEquals(listOf(pi), r.prefsStore.load().servers)
        assertEquals(pi, r.prefsStore.load().server)
        assertEquals(ServerKind.PI, r.model.current.kind)
        // Restoring twice is the same as restoring once: a snackbar can be actioned late.
        r.model.restoreServer(removed)
        runCurrent()
        assertEquals(listOf(pi), r.prefsStore.load().servers)

        // Removing one that is not there hands back nothing to undo.
        assertNull(r.model.removeServer(ServerAddress.parseOrNull("https://gone.example")!!))
    }

    @Test fun `a QR link parses only on the app's own host, and only as an address`() {
        val host = "c.example"
        fun p(url: String) = ServerLink.parse(url, host)
        assertEquals(
            ServerAddress.parseOrNull(pi),
            (p("https://c.example/add?server=http%3A%2F%2Fpi.local%3A5000") as ServerLink.Result.Ok).address,
        )
        // Unescaped is what a hand-written poster carries; it parses the same.
        assertIs<ServerLink.Result.Ok>(p("https://c.example/add?server=$pi"))
        assertIs<ServerLink.Result.Ok>(p("https://C.Example/add/?server=https://x.example"))
        // Another host cannot mint a code that adds a server — the authority is the app's.
        assertEquals(ServerLink.Result.Invalid, p("https://evil.example/add?server=$pi"))
        // Nor can a downgraded link: the App Link is verified for `https` and nothing else.
        assertEquals(ServerLink.Result.Invalid, p("http://c.example/add?server=$pi"))
        assertEquals(ServerLink.Result.Invalid, p("https://c.example/address?server=$pi"))
        assertEquals(ServerLink.Result.Invalid, p("https://c.example/add?meet=m1"))
        assertEquals(ServerLink.Result.Invalid, p("https://c.example/add?server="))
        assertEquals(ServerLink.Result.Invalid, p("https://c.example/add?server=ftp://x"))
        // The cleartext floor is the typed address's, and a printed code cannot lower it.
        assertEquals(ServerLink.Result.CleartextNotLocal, p("https://c.example/add?server=http://203.0.113.10:5000"))
        // A parameter that only starts the same is not the parameter.
        assertEquals(ServerLink.Result.Invalid, p("https://c.example/add?servers=$pi"))
    }

    // ── P-11: the picker and the default ─────────────────────────────────────

    @Test fun `P-11 the picker names the server only when it is not the default`() = runTest {
        val other = "https://x.example"
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.cloudRoutes(other)
        r.model.start()
        runCurrent()
        assertNull(r.model.current.pickerServerName)
        r.model.selectServer(ServerAddress.parseOrNull(other)!!)
        runCurrent()
        assertEquals("x.example", r.model.current.pickerServerName)
        // Compared by normalised origin: the default spelled differently is still the default.
        r.model.selectServer(ServerAddress.parseOrNull("HTTPS://C.Example:443/")!!)
        runCurrent()
        assertTrue(r.model.current.isDefaultServer)
        assertNull(r.model.current.pickerServerName)
        assertNull(r.prefsStore.load().server)
    }

    @Test fun `a QR link asks before it adds, and the yes runs the handshake`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v2","app":"v2"}}""")
        r.http.on("$pi/config", body = """{"meet_title":"Pool","num_lanes":8}""")
        r.http.on("$pi/schedule.json", body = """{"heats":[]}""")

        // P-16: scanning names the server and does nothing else — no save, no select, and
        // no request to the address either. Only the question is on screen.
        val before = r.http.calls.size
        r.model.openServerLink("https://c.example/add?server=$pi")
        runCurrent()
        val invite = assertNotNull(r.model.current.invite)
        assertEquals(pi, invite.address?.origin)
        assertEquals(ServerInvite.Standing.NEW, invite.standing)
        assertNull(invite.failure)
        assertEquals(before, r.http.calls.size)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // A no leaves the app exactly as it found it.
        r.model.dismissInvite()
        runCurrent()
        assertNull(r.model.current.invite)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // A yes is P-13's path and nothing new: GET /server, then save, then select.
        r.model.openServerLink("https://c.example/add?server=$pi")
        r.model.acceptInvite()
        runCurrent()
        assertNull(r.model.current.invite)
        assertEquals(listOf(pi), r.prefsStore.load().servers)
        assertEquals(pi, r.prefsStore.load().server)
        assertEquals(ServerKind.PI, r.model.current.kind)

        // Scanning the same code again, now that this server is the one in use and
        // answering, has nothing to offer: the prompt says where the reader already is and
        // carries one button. It asks the network **nothing** — a handshake here could only
        // fail, and a poster is scanned on a deck where the wifi is worst, so the one thing
        // it could produce is "cannot reach this server" over a live heat from that server.
        val quiet = r.http.calls.size
        r.model.openServerLink("https://c.example/add?server=$pi")
        runCurrent()
        assertEquals(ServerInvite.Standing.IN_USE, assertNotNull(r.model.current.invite).standing)
        // ...and the yes it does not offer does nothing if it is called anyway.
        r.model.acceptInvite()
        runCurrent()
        assertEquals(quiet, r.http.calls.size)
        assertFalse(r.model.current.invite!!.checking)
        assertNotNull(r.model.current.meet)
        r.model.dismissInvite()

        // A server in the list that is *not* the one in use still asks to switch.
        r.model.selectServer(ServerAddress.parseOrNull(cloud)!!)
        runCurrent()
        r.model.openServerLink("https://c.example/add?server=$pi")
        runCurrent()
        assertEquals(ServerInvite.Standing.LISTED, assertNotNull(r.model.current.invite).standing)
        r.model.dismissInvite()

        // And so does the one in use whose handshake has failed — that is a Pi that
        // rebooted, where the useful answer is the reconnect and not "all is well".
        r.model.selectServer(ServerAddress.parseOrNull(pi)!!)
        r.http.routes.remove("$pi/server")
        r.model.selectServer(ServerAddress.parseOrNull(cloud)!!)
        runCurrent()
        r.model.selectServer(ServerAddress.parseOrNull(pi)!!)
        runCurrent()
        assertNotNull(r.model.current.serverError)
        r.model.openServerLink("https://c.example/add?server=$pi")
        runCurrent()
        assertEquals(ServerInvite.Standing.LISTED, assertNotNull(r.model.current.invite).standing)
        r.model.dismissInvite()
    }

    @Test fun `a link that fails says so in the prompt rather than closing it`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()

        // A code that opens the app and then appears to do nothing is the worst outcome:
        // the reader cannot tell it from a dead app. So a bad link still raises the prompt.
        r.model.openServerLink("https://c.example/add?server=nope%20://")
        runCurrent()
        assertEquals(InviteFailure.BAD_LINK, r.model.current.invite?.failure)
        assertNull(r.model.current.invite?.address)
        r.model.openServerLink("https://c.example/add?server=http://203.0.113.10:5000")
        runCurrent()
        assertEquals(InviteFailure.CLEARTEXT_NOT_LOCAL, r.model.current.invite?.failure)

        // An address that will not answer fails *in* the dialog, which stays open: closing
        // it would leave the picker looking untouched and the reader with no idea why.
        r.model.openServerLink("https://c.example/add?server=https://nowhere.example")
        r.model.acceptInvite()
        runCurrent()
        val invite = assertNotNull(r.model.current.invite)
        assertEquals(InviteFailure.UNREACHABLE, invite.failure)
        assertFalse(invite.checking)
        assertEquals("https://nowhere.example", invite.address?.origin)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // An address that answers but is not Splouch is worded differently from a fault.
        r.http.on("https://web.example/server", status = 404)
        r.model.openServerLink("https://c.example/add?server=https://web.example")
        r.model.acceptInvite()
        runCurrent()
        assertEquals(InviteFailure.NOT_SPLOUCH, r.model.current.invite?.failure)
    }

    @Test fun `C-08 a re-fetched config with another lane count resizes both boards in place`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        r.model.openMeet("m1")
        runCurrent()
        val session = r.model.current.meet!!.session
        assertEquals(6, session.scoreboard.value.lanes.size)

        r.http.on(
            "https://c.example/meet/m1/config",
            body = """{"name":"Meet One","live":true,"settings":{"num_lanes":8,"locale":"en"}}""",
        )
        r.model.refreshMeet()
        runCurrent()
        assertSame(session, r.model.current.meet!!.session)
        assertEquals((1..8).toList(), session.scoreboard.value.lanes.map { it.number })
        assertEquals(8, session.resultsView.value.rows.size)
        assertEquals(3, r.transport.connections.count { !it.closed })
    }

    @Test fun `C-05 a network back retries a meet list that failed under a server that answered`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.routes.remove("https://c.example/meets")
        r.model.start()
        runCurrent()
        assertNotNull(r.model.current.picker.error)

        r.http.cloudRoutes()
        r.model.networkRestored()
        runCurrent()
        assertNull(r.model.current.picker.error)
        assertEquals(listOf("m1", "m2"), r.model.current.picker.meets.map { it.id })
    }

    @Test fun `an unreachable server is an error, not a crash, and the tab choice persists`() = runTest {
        val r = Rig(this)
        r.model.start()
        runCurrent()
        assertNotNull(r.model.current.serverError)
        assertNull(r.model.current.kind)
        r.model.setTab(MeetTab.SCHEDULE)
        assertEquals(MeetTab.SCHEDULE, r.prefsStore.load().tab)
    }

    @Test fun `the picker's search survives a meet and a refresh, and closing it clears the query`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        r.model.openPickerSearch()
        r.model.setPickerQuery("meet")
        r.model.openMeet("m1")
        runCurrent()
        assertNotNull(r.model.current.meet)
        r.model.closeMeet()
        r.model.refreshPicker()
        runCurrent()
        assertTrue(r.model.current.picker.searching)
        assertEquals("meet", r.model.current.picker.query)
        r.model.closePickerSearch()
        assertFalse(r.model.current.picker.searching)
        assertEquals("", r.model.current.picker.query)
    }

    // ── P-06, P-07, C-10, P-20 (app.md v3, amended) ──────────────────────────

    @Suppress("ktlint:standard:max-line-length")
    private fun StubHttp.pickerConfig(
        analytics: Boolean,
        strings: String = """"results_disclaimer":"D","privacy_note":"P"""",
    ) = on(
        "$cloud/picker/config?lang=fr",
        body = """{"title":"Splouch","lang":"fr","analytics_enabled":$analytics,"strings":{$strings}}""",
    )

    @Test fun `P-06 is one line from the server's words, nothing before the server has answered`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.routes.remove("$cloud/picker/config?lang=fr")
        r.model.start()
        runCurrent()
        // No config — a first launch offline — means no line, not the snapshot's copy.
        assertNull(r.model.current.disclaimer)
        r.http.pickerConfig(true, """"results_disclaimer":"D","results_disclaimer_short":"Short D"""")
        r.model.refreshPicker()
        runCurrent()
        assertEquals("Short D", r.model.current.disclaimer!!.short)
        assertEquals("D", r.model.current.disclaimer!!.full)
    }

    @Test fun `an older server's missing short line comes from the snapshot, in the reader's language`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        assertEquals("Résultats non officiels", r.model.current.disclaimer!!.short)
    }

    @Test fun `P-07 offers the privacy section only while the server counts, and keeps the choice`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        assertTrue(r.model.current.countingOffered)
        assertEquals("P", r.model.current.privacyNote)
        r.model.setCounting(false)
        r.http.pickerConfig(false)
        r.model.refreshPicker()
        runCurrent()
        assertFalse(r.model.current.countingOffered)
        assertNull(r.model.current.privacyNote)
        // Hidden, not forgotten.
        assertFalse(r.vids.counting(cloud))
        assertFalse(r.model.current.counting)
    }

    @Test fun `C-10 off from settings deletes this server's vid, back on makes a new one`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        assertTrue(r.model.current.counting)
        val first = r.vids.vid(cloud)!!
        r.model.setCounting(false)
        assertFalse(r.model.current.counting)
        assertNull(r.vids.entries[cloud]!!.id)
        assertNull(r.vids.entries[cloud]!!.createdAt)
        r.model.setCounting(true)
        val second = r.vids.vid(cloud)!!
        assertNotEquals(first, second)
    }

    @Test fun `C-10 the setting is per server`() = runTest {
        val other = "https://x.example"
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.cloudRoutes(other)
        r.model.start()
        runCurrent()
        r.model.setCounting(false)
        r.model.selectServer(ServerAddress.parseOrNull(other)!!)
        runCurrent()
        assertTrue(r.model.current.counting)
        r.model.selectServer(ServerAddress.parseOrNull(cloud)!!)
        runCurrent()
        assertFalse(r.model.current.counting)
    }

    @Test fun `P-20 waits for the server, then shows once`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.routes.remove("$cloud/picker/config?lang=fr")
        r.model.start()
        runCurrent()
        // Offline first launch: postponed, and not marked seen.
        assertFalse(r.model.current.introOpen)
        assertFalse(r.prefsStore.load().introSeen)
        r.model.replayIntro() // nor can it be replayed without the server's words
        assertFalse(r.model.current.introOpen)

        r.http.cloudRoutes()
        r.model.refreshPicker()
        runCurrent()
        assertTrue(r.model.current.introOpen)
        r.model.finishIntro()
        assertFalse(r.model.current.introOpen)
        assertTrue(r.prefsStore.load().introSeen)
        // Skipping is not consent either way: counting is as it was.
        assertTrue(r.model.current.counting)

        r.model.refreshPicker()
        runCurrent()
        assertFalse(r.model.current.introOpen)
        r.model.replayIntro()
        assertTrue(r.model.current.introOpen)
    }

    @Test fun `P-20 seen on a previous launch stays seen`() = runTest {
        val r = Rig(this, Preferences(introSeen = true))
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        assertFalse(r.model.current.introOpen)
    }

    // ── app.md v3: C-11, C-12, A-09, A-12 ─────────────────────────────────────

    private val configOne = """{"name":"Meet One","live":true,"settings":{"num_lanes":6,"locale":"en"}}"""

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `C-11 a meet opens at the base its list entry names, and A-09 asks that base`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.on(
            "$cloud/meets",
            body = """{"meets":[{"id":"m1","name":"Meet One","offline":false,"country":"CA","province":"QC","base":"https://w.example/w2"}]}""",
        )
        r.http.on("https://w.example/w2/meet/m1/config", body = configOne)
        r.http.on("https://w.example/w2/meet/m1/schedule", body = """{"heats":[]}""")
        r.model.start()
        runCurrent()
        val listed = r.model.current.picker.meets.single()
        assertEquals("CA" to "QC", listed.country to listed.province)
        r.model.openMeet("m1")
        runCurrent()
        val m = r.model.current.meet!!
        assertEquals("https://w.example/w2", m.context.base.url)
        assertEquals(emptyList(), m.schedule)
        val urls = r.http.calls.map { it.first }
        assertTrue("https://w.example/w2/meet/m1/config" in urls)
        assertTrue("https://w.example/w2/meet/m1/schedule" in urls)
        assertFalse(urls.any { it.startsWith("$cloud/meet/") })
        // strings stay on the server the list came from
        assertFalse(urls.any { it.startsWith("https://w.example/w2/i18n") })
        assertTrue(r.transport.connections.all { it.url.startsWith("wss://w.example/w2/ws/") })

        // A-09: the 404 that closes the meet is the base's
        r.http.on("https://w.example/w2/meet/m1/config", status = 404, body = "{}")
        r.model.foreground()
        runCurrent()
        assertNull(r.model.current.meet)
        assertTrue(r.model.current.meetGone)
    }

    @Test fun `C-11 an entry with no base, or one that fails the cleartext floor, opens at the server URL`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.on(
            "$cloud/meets",
            body = """{"meets":[{"id":"m1","name":"Meet One","offline":false,"base":"http://w.example/w2"}]}""",
        )
        r.model.start()
        runCurrent()
        r.model.openMeet("m1")
        runCurrent()
        assertEquals(MeetBase.parse(cloud), r.model.current.meet!!.context.base)
        assertTrue(r.transport.connections.all { it.url.startsWith("wss://c.example/ws/") })
    }

    @Test fun `C-12 a moved reconnects all three sockets at the new base and re-fetches config there, never gone`() =
        runTest {
            val r = Rig(this)
            r.http.cloudRoutes()
            r.http.on("https://w.example/w3/meet/m1/config", body = configOne)
            r.http.on("https://w.example/w3/meet/m1/schedule", body = """{"heats":[]}""")
            r.model.start()
            runCurrent()
            r.model.openMeet("m1")
            runCurrent()
            val session = r.model.current.meet!!.session
            r.transport.connections.forEach { it.serverOpen() }
            runCurrent()
            r.transport.connections[1].serverSend(
                """{"event":"moved","data":{"url":"https://w.example/w3/mobile?meet=m1","base":"https://w.example/w3"}}""",
            )
            runCurrent()
            val m = r.model.current.meet!!
            assertTrue(m.session === session)
            assertFalse(r.model.current.meetGone)
            assertEquals("https://w.example/w3", m.context.base.url)
            assertTrue(r.transport.connections.take(3).all { it.closed })
            assertEquals(3, r.transport.connections.drop(3).size)
            assertTrue(r.transport.connections.drop(3).all { it.url.startsWith("wss://w.example/w3/ws/") })
            assertTrue("https://w.example/w3/meet/m1/config" in r.http.calls.map { it.first })
            // the old worker's 404 for the meet it let go is never asked again
            r.http.on("$cloud/meet/m1/config", status = 404, body = "{}")
            r.model.foreground()
            runCurrent()
            assertNotNull(r.model.current.meet)
            assertFalse(r.model.current.meetGone)
        }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `A-09 a config naming another base is followed as a moved`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.http.on(
            "$cloud/meet/m1/config",
            body = """{"name":"Meet One","live":true,"base":"https://w.example/w4","settings":{"num_lanes":6,"locale":"en"}}""",
        )
        r.http.on("https://w.example/w4/meet/m1/config", body = configOne)
        r.http.on("https://w.example/w4/meet/m1/schedule", body = """{"heats":[]}""")
        r.model.start()
        runCurrent()
        r.model.openMeet("m1")
        runCurrent()
        // the first config already named the new base: opened there, no socket at the old one
        assertEquals("https://w.example/w4", r.model.current.meet!!.context.base.url)
        assertEquals(3, r.transport.connections.size)
        assertTrue(r.transport.connections.all { it.url.startsWith("wss://w.example/w4/ws/") })

        // and on a later fetch: the meet moved on while open
        r.http.on(
            "https://w.example/w4/meet/m1/config",
            body = """{"name":"Meet One","live":true,"base":"https://w.example/w5","settings":{"num_lanes":6,"locale":"en"}}""",
        )
        r.http.on("https://w.example/w5/meet/m1/config", body = configOne)
        r.http.on("https://w.example/w5/meet/m1/schedule", body = """{"heats":[]}""")
        r.model.refreshMeet()
        runCurrent()
        val m = r.model.current.meet!!
        assertEquals("https://w.example/w5", m.context.base.url)
        assertFalse(m.refreshing)
        assertTrue(r.transport.connections.take(3).all { it.closed })
        assertTrue(r.transport.connections.drop(3).all { it.url.startsWith("wss://w.example/w5/ws/") })
        // an empty base (a worker that does not know its own address) moves nothing
        r.http.on(
            "https://w.example/w5/meet/m1/config",
            body = configOne.replace("{\"name\"", "{\"base\":\"\",\"name\""),
        )
        r.model.foreground()
        runCurrent()
        assertEquals(6, r.transport.connections.size)
    }

    @Test fun `A-12 back keeps the meet while the list fails or hangs, and works once it answers`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start()
        runCurrent()
        r.model.openMeet("m1")
        runCurrent()

        // fails
        r.http.on("$cloud/meets", status = 503)
        r.model.leaveMeet()
        runCurrent()
        assertNotNull(r.model.current.meet)
        assertTrue(r.model.current.pickerUnavailable)
        r.model.dismissPickerUnavailable()

        // hangs: nothing for ~4 s, then the notice, and still on the meet
        r.http.hanging += "$cloud/meets"
        r.model.leaveMeet()
        runCurrent()
        advanceTimeBy(3_900.milliseconds)
        runCurrent()
        assertNotNull(r.model.current.meet)
        assertFalse(r.model.current.pickerUnavailable)
        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertNotNull(r.model.current.meet)
        assertTrue(r.model.current.pickerUnavailable)
        r.model.dismissPickerUnavailable()

        // an abandoned back gesture raises nothing
        val gesture = backgroundScope.launch { r.model.pickerReachable() }
        runCurrent()
        gesture.cancel()
        advanceTimeBy(5_000.milliseconds)
        runCurrent()
        assertFalse(r.model.current.pickerUnavailable)

        // the list answers: back works, and lands on the fresh list
        r.http.hanging.clear()
        r.http.on("$cloud/meets", body = """{"meets":[{"id":"m3","name":"New","offline":false}]}""")
        r.model.leaveMeet()
        runCurrent()
        assertNull(r.model.current.meet)
        assertFalse(r.model.current.pickerUnavailable)
        assertEquals(listOf("m3"), r.model.current.picker.meets.map { it.id })
    }
}
