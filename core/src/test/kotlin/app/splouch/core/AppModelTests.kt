package app.splouch.core

import app.splouch.core.session.AddServerResult
import app.splouch.core.session.AppModel
import app.splouch.core.session.InMemoryPreferencesStore
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.Preferences
import app.splouch.core.session.ServerAddress
import app.splouch.core.strings.InMemoryBundleCache
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
        r.model.setLabelStyle("short"); runCurrent()
        assertEquals("ÉP", r.model.current.meet!!.labels["event"])
        assertEquals("short", r.prefsStore.load().labelStyle)
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
        r.model.removeServer(ServerAddress.parseOrNull(pi)!!); runCurrent()
        assertTrue(r.model.current.isDefaultServer)
        assertNull(r.prefsStore.load().server)
    }

    @Test fun `an unreachable server is an error, not a crash, and the tab choice persists`() = runTest {
        val r = Rig(this)
        r.model.start(); runCurrent()
        assertNotNull(r.model.current.serverError)
        assertNull(r.model.current.kind)
        r.model.setTab(2)
        assertEquals(2, r.prefsStore.load().tab)
    }
}
