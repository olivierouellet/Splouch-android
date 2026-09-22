package app.splouch.core

import app.splouch.core.session.AddServerResult
import app.splouch.core.session.AppModel
import app.splouch.core.session.InMemoryPreferencesStore
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.InviteFailure
import app.splouch.core.session.MeetTab
import app.splouch.core.session.Preferences
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.ServerLink
import app.splouch.core.strings.InMemoryBundleCache
import app.splouch.core.strings.Labels
import app.splouch.core.support.FakeTransport
import app.splouch.core.support.StubHttp
import app.splouch.core.wire.ServerKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppModelTests {
    private val cloud = "https://c.example"
    private val pi = "http://pi.local:5000"

    private class Rig(scope: TestScope, prefs: Preferences = Preferences()) {
        val http = StubHttp()
        val transport = FakeTransport()
        val prefsStore = InMemoryPreferencesStore(prefs)
        val model = AppModel(
            ServerAddress.parseOrNull("https://c.example")!!, http, transport, InMemoryVidStore(), prefsStore,
            InMemoryBundleCache(), scope.backgroundScope, deviceLang = "fr", timeSource = scope.testScheduler.timeSource,
        )
    }

    private fun StubHttp.cloudRoutes(base: String = "https://c.example") {
        on("$base/server", body = """{"kind":"cloud","name":"Cloud","contract":{"api":"v2","app":"v1"}}""")
        on("$base/meets", body = """{"meets":[{"id":"m1","name":"Meet One","offline":false},{"id":"m2","name":"Old","offline":true}]}""")
        on("$base/picker/config?lang=fr", body = """{"title":"Splouch","lang":"fr","analytics_enabled":true,"strings":{"results_disclaimer":"D","privacy_note":"P"}}""")
        on("$base/servers", body = """{"servers":[{"name":"Cloud","url":"$base","kind":"cloud"},{"name":"Club X","url":"https://x.example","kind":"cloud"}]}""")
        on("$base/locales", body = """[{"code":"en","name":"English"},{"code":"fr","name":"Français"}]""")
        on("$base/i18n/fr", body = """{"lang":"fr","mobile":{"scoreboard":"Tableau"},"labels":{"short":{"event":"ÉP","heat":"SÉR"},"long":{"event":"ÉPREUVE","heat":"SÉRIE"}}}""", headers = mapOf("ETag" to "\"e1\""))
        on("$base/i18n/en", body = """{"lang":"en","mobile":{"scoreboard":"Scoreboard"},"labels":{"short":{"event":"EV","heat":"HT"},"long":{"event":"EVENT","heat":"HEAT"}}}""")
        on("$base/meet/m1/config", body = """{"name":"Meet One","live":true,"settings":{"num_lanes":6,"locale":"en","labels":{"event":"EV","heat":"HT"},"label_style":"short"}}""")
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

    @Test fun `opening a meet starts a session in the meet's language with long headers over the operator's labels`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start(); runCurrent()
        r.model.openMeet("m1"); runCurrent()
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
        r.model.setLang("fr"); runCurrent()
        val m2 = r.model.current.meet!!
        assertEquals("fr", m2.lang)
        assertEquals("Tableau", m2.strings.mobile("scoreboard"))
        assertEquals("ÉPREUVE", m2.labels["event"])
        // T-09 is withdrawn, not deleted: the choice is still stored, and still read
        // over by `effectiveLabelStyle`, so the header stays long while the control is away.
        r.model.setLabelStyle("short"); runCurrent()
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
        r.model.start(); runCurrent()
        r.model.openMeet("m1"); runCurrent()
        assertNotNull(r.model.current.meet)
        r.http.on("https://c.example/meet/m1/config", status = 404, body = "{}")
        r.model.foreground(); runCurrent()
        assertNull(r.model.current.meet)
        assertTrue(r.model.current.meetGone)
        // a network fault on the same call is not a gone meet
        r.model.dismissMeetGone()
        r.http.cloudRoutes()
        r.model.openMeet("m1"); runCurrent()
        r.http.on("https://c.example/meet/m1/config", status = 503, body = "")
        r.model.foreground(); runCurrent()
        assertNotNull(r.model.current.meet)
        assertFalse(r.model.current.meetGone)
    }

    @Test fun `A-11 the Results tab comes and goes with the console, on the re-fetch the app already makes`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start(); runCurrent()
        r.model.openMeet("m1"); runCurrent()
        fun tabs() = MeetTab.of(r.model.current.meet!!.config)
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.RESULTS, MeetTab.SCHEDULE), tabs())

        // The operator unplugs the console mid-meet and drives the boards from /manual.
        // The Pi re-registers, the cloud's config changes, and the app finds out on the
        // fetch it was already going to make — here, coming back to the foreground.
        fun console(json: String) = r.http.on("https://c.example/meet/m1/config",
            body = """{"name":"Meet One","live":true,"settings":{"num_lanes":6,"locale":"en","console":$json}}""")
        console("""{"key":"manual","timed":false}""")
        r.model.foreground(); runCurrent()
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.SCHEDULE), tabs())
        // The meet itself is untouched: same session, same sockets, nothing re-opened.
        assertEquals(3, r.transport.connections.count { !it.closed })

        // Pull-to-refresh (A-05) is the same fetch and reads the same flag.
        console("""{"key":"cts_gen6","timed":true}""")
        r.model.refreshMeet(); runCurrent()
        assertEquals(3, tabs().size)
        assertFalse(r.model.current.meet!!.refreshing)

        // A network fault changes nothing — least of all takes a tab away.
        r.http.on("https://c.example/meet/m1/config", status = 503, body = "")
        r.model.refreshMeet(); runCurrent()
        assertEquals(3, tabs().size)
    }

    @Test fun `a pi goes straight to the board, joins nothing, and shows a contract notice once`() = runTest {
        val r = Rig(this, Preferences(server = pi))
        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v1","app":"v1"}}""")
        r.http.on("$pi/config", body = """{"meet_title":"Pool Meet","num_lanes":8,"locale":"fr","labels":{"event":"ÉP"}}""")
        r.http.on("$pi/schedule.json", body = """{"heats":[]}""")
        r.model.start(); runCurrent()
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

    @Test fun `adding a server checks it first and only then saves and selects it`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start(); runCurrent()
        assertEquals(AddServerResult.InvalidAddress, r.model.addServer("not a url at all ://"))
        assertEquals(AddServerResult.CleartextNotLocal, r.model.addServer("http://192.168.1.10:5000"))
        assertIs<AddServerResult.Unreachable>(r.model.addServer("https://nowhere.example"))
        assertEquals(emptyList(), r.prefsStore.load().servers)
        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v2","app":"v1"}}""")
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
        val removed = r.model.removeServer(ServerAddress.parseOrNull(pi)!!); runCurrent()
        assertNotNull(removed)
        assertEquals(0, removed.index)
        assertTrue(removed.wasSelected)
        assertTrue(r.model.current.isDefaultServer)
        assertNull(r.prefsStore.load().server)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // ...and undo puts it back where it was, and back in use, without asking the
        // network again — the handshake already happened when it was added.
        r.model.restoreServer(removed); runCurrent()
        assertEquals(listOf(pi), r.prefsStore.load().servers)
        assertEquals(pi, r.prefsStore.load().server)
        assertEquals(ServerKind.PI, r.model.current.kind)
        // Restoring twice is the same as restoring once: a snackbar can be actioned late.
        r.model.restoreServer(removed); runCurrent()
        assertEquals(listOf(pi), r.prefsStore.load().servers)

        // Removing one that is not there hands back nothing to undo.
        assertNull(r.model.removeServer(ServerAddress.parseOrNull("https://gone.example")!!))
    }

    @Test fun `a QR link parses only on the app's own host, and only as an address`() {
        val host = "c.example"
        fun p(url: String) = ServerLink.parse(url, host)
        assertEquals(ServerAddress.parseOrNull(pi), (p("https://c.example/add?server=http%3A%2F%2Fpi.local%3A5000") as ServerLink.Result.Ok).address)
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
        assertEquals(ServerLink.Result.CleartextNotLocal, p("https://c.example/add?server=http://192.168.1.10:5000"))
        // A parameter that only starts the same is not the parameter.
        assertEquals(ServerLink.Result.Invalid, p("https://c.example/add?servers=$pi"))
    }

    @Test fun `a QR link asks before it adds, and the yes runs the handshake`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start(); runCurrent()
        r.http.on("$pi/server", body = """{"kind":"pi","name":"Piscine","contract":{"api":"v2","app":"v1"}}""")
        r.http.on("$pi/config", body = """{"meet_title":"Pool","num_lanes":8}""")
        r.http.on("$pi/schedule.json", body = """{"heats":[]}""")

        // P-16: scanning names the server and does nothing else — no save, no select, and
        // no request to the address either. Only the question is on screen.
        val before = r.http.calls.size
        r.model.openServerLink("https://c.example/add?server=$pi"); runCurrent()
        val invite = assertNotNull(r.model.current.invite)
        assertEquals(pi, invite.address?.origin)
        assertFalse(invite.known)
        assertNull(invite.failure)
        assertEquals(before, r.http.calls.size)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // A no leaves the app exactly as it found it.
        r.model.dismissInvite(); runCurrent()
        assertNull(r.model.current.invite)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // A yes is P-13's path and nothing new: GET /server, then save, then select.
        r.model.openServerLink("https://c.example/add?server=$pi")
        r.model.acceptInvite(); runCurrent()
        assertNull(r.model.current.invite)
        assertEquals(listOf(pi), r.prefsStore.load().servers)
        assertEquals(pi, r.prefsStore.load().server)
        assertEquals(ServerKind.PI, r.model.current.kind)

        // Scanning the same code again asks to *switch*, since the list already offers it.
        r.model.openServerLink("https://c.example/add?server=$pi"); runCurrent()
        assertTrue(assertNotNull(r.model.current.invite).known)
        r.model.dismissInvite()
    }

    @Test fun `a link that fails says so in the prompt rather than closing it`() = runTest {
        val r = Rig(this)
        r.http.cloudRoutes()
        r.model.start(); runCurrent()

        // A code that opens the app and then appears to do nothing is the worst outcome:
        // the reader cannot tell it from a dead app. So a bad link still raises the prompt.
        r.model.openServerLink("https://c.example/add?server=nope%20://"); runCurrent()
        assertEquals(InviteFailure.BAD_LINK, r.model.current.invite?.failure)
        assertNull(r.model.current.invite?.address)
        r.model.openServerLink("https://c.example/add?server=http://192.168.1.10:5000"); runCurrent()
        assertEquals(InviteFailure.CLEARTEXT_NOT_LOCAL, r.model.current.invite?.failure)

        // An address that will not answer fails *in* the dialog, which stays open: closing
        // it would leave the picker looking untouched and the reader with no idea why.
        r.model.openServerLink("https://c.example/add?server=https://nowhere.example")
        r.model.acceptInvite(); runCurrent()
        val invite = assertNotNull(r.model.current.invite)
        assertEquals(InviteFailure.UNREACHABLE, invite.failure)
        assertFalse(invite.checking)
        assertEquals("https://nowhere.example", invite.address?.origin)
        assertEquals(emptyList(), r.prefsStore.load().servers)

        // An address that answers but is not Splouch is worded differently from a fault.
        r.http.on("https://web.example/server", status = 404)
        r.model.openServerLink("https://c.example/add?server=https://web.example")
        r.model.acceptInvite(); runCurrent()
        assertEquals(InviteFailure.NOT_SPLOUCH, r.model.current.invite?.failure)
    }

    @Test fun `an unreachable server is an error, not a crash, and the tab choice persists`() = runTest {
        val r = Rig(this)
        r.model.start(); runCurrent()
        assertNotNull(r.model.current.serverError)
        assertNull(r.model.current.kind)
        r.model.setTab(MeetTab.SCHEDULE)
        assertEquals(MeetTab.SCHEDULE, r.prefsStore.load().tab)
    }
}
