package app.splouch.core

import app.splouch.core.follows.FollowLead
import app.splouch.core.follows.FollowRegistration
import app.splouch.core.follows.FollowState
import app.splouch.core.follows.FollowStore
import app.splouch.core.follows.FollowedSwimmer
import app.splouch.core.follows.HeatFocus
import app.splouch.core.follows.InMemoryFollowStore
import app.splouch.core.follows.MeetFollows
import app.splouch.core.follows.PushPermission
import app.splouch.core.session.AppModel
import app.splouch.core.session.InMemoryPreferencesStore
import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.MeetTab
import app.splouch.core.session.Preferences
import app.splouch.core.session.ServerAddress
import app.splouch.core.strings.InMemoryBundleCache
import app.splouch.core.support.FakeTransport
import app.splouch.core.support.StubHttp
import app.splouch.core.wire.MeetConfig
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScheduleLane
import app.splouch.core.wire.ScheduleSwimmer
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * app.md §10, heat notifications: what the device keeps (N-02, N-09), what it sends and
 * where (N-07, api.md §5.13), when the bell shows (N-01), and the tap (N-08).
 */
class FollowsTests {
    private val emma = FollowedSwimmer("Emma Roy", "CNQ")
    private val base = "https://c.example"

    private class Rig(scope: TestScope, val store: InMemoryFollowStore = InMemoryFollowStore()) {
        val http = StubHttp()
        val model = AppModel(
            ServerAddress.parseOrNull("https://c.example")!!, http, FakeTransport(), InMemoryVidStore(),
            InMemoryPreferencesStore(Preferences()), InMemoryBundleCache(), scope.backgroundScope,
            deviceLang = "fr", timeSource = scope.testScheduler.timeSource, followStore = store,
        )
    }

    @Suppress("ktlint:standard:max-line-length")
    private fun StubHttp.routes(push: String = """["apns","fcm"]""") {
        on("$base/server", body = """{"kind":"cloud","name":"Cloud","contract":{"api":"v2","app":"v3"}}""")
        on("$base/meets", body = """{"meets":[{"id":"m1","name":"Meet One"}]}""")
        on("$base/picker/config?lang=fr", body = """{"title":"Splouch","lang":"fr","strings":{}}""")
        on("$base/servers", body = """{"servers":[]}""")
        on("$base/locales", body = """[]""")
        on("$base/i18n/fr", body = """{"lang":"fr","mobile":{}}""")
        on(
            "$base/meet/m1/config",
            body = """{"name":"Meet One","push":$push,"settings":{"num_lanes":6,"locale":"fr"}}""",
        )
        on("$base/meet/m1/schedule", body = """{"heats":[]}""")
        on("$base/meet/m1/follow", status = 204)
    }

    private fun Rig.openWithPush(scope: TestScope, permission: PushPermission = PushPermission.ALLOWED) {
        http.routes()
        model.setPushAvailable(true)
        model.setPushPermission(permission)
        model.setPushToken("tok")
        model.start()
        scope.runCurrent()
        http.puts.clear() // what the token's arrival sent (N-07), not the open
        model.openMeet("m1")
        scope.runCurrent()
    }

    private fun StubHttp.followBodies(url: String = "$base/meet/m1/follow"): List<JsonObject> =
        puts.filter { it.first == url }.map { parseJsonOrNull(it.second) as JsonObject }

    // ── wire ──────────────────────────────────────────────────────────────────

    @Test fun `the registration is the contract's body`() {
        val body = FollowRegistration("tok", "fr", MeetFollows(listOf(emma), FollowLead(true, 2), selected = false))
            .toJson()
        assertEquals("fcm", body["platform"]!!.jsonPrimitive.content)
        assertEquals("tok", body["token"]!!.jsonPrimitive.content)
        assertEquals("fr", body["lang"]!!.jsonPrimitive.content)
        assertEquals("CNQ", body["swimmers"]!!.jsonArray[0].jsonObject["club"]!!.jsonPrimitive.content)
        assertEquals("2", body["lead"]!!.jsonObject["heats"]!!.jsonPrimitive.content)
        assertNull(body["lead"]!!.jsonObject["minutes"])
        assertEquals("false", body["selected"]!!.jsonPrimitive.content)
    }

    @Test fun `the config says which platforms the node can notify`() {
        val c = MeetConfig.fromCloudJson(parseJsonOrNull("""{"name":"x","push":["fcm"],"settings":{}}"""))!!
        assertEquals(listOf("fcm"), c.push)
        assertEquals(emptyList(), MeetConfig.fromCloudJson(parseJsonOrNull("""{"name":"x","settings":{}}"""))!!.push)
    }

    // ── the device ────────────────────────────────────────────────────────────

    @Test fun `follows survive the store's text and are kept per server and meet`() {
        val all = mapOf(
            FollowStore.key("https://a", "m1") to MeetFollows(listOf(emma), FollowLead(true, 3), false, "https://a/w2"),
        )
        assertEquals(all, FollowStore.decode(FollowStore.encode(all)))
        assertEquals("https://a" to "m1", FollowStore.split("https://a|m1"))
        val store = InMemoryFollowStore()
        store.set("https://a", "m1", MeetFollows(listOf(emma)))
        assertTrue(store.get("https://b", "m1").isEmpty)
        store.set("https://a", "m1", MeetFollows())
        assertTrue(store.load().isEmpty(), "an empty list is no row")
    }

    @Test fun `a filter chip becomes one follow per club`() {
        val heats = listOf(
            ScheduleHeat(
                "1",
                "1",
                "",
                null,
                "",
                listOf(
                    ScheduleLane(1, "Emma Roy", "CNQ", "", emptyList()),
                    ScheduleLane(2, "MEGO A", "MEGO", "", listOf(ScheduleSwimmer("Emma Roy", "Emma"))),
                ),
            ),
        )
        assertEquals(listOf("CNQ", "MEGO"), MeetFollows.swimmersNamed("Emma Roy", heats).map { it.club })
    }

    // ── N-01, N-04, N-07 ──────────────────────────────────────────────────────

    @Test fun `the bell needs an fcm node and a build that can receive`() = runTest {
        val rig = Rig(this)
        rig.http.routes(push = """["apns"]""")
        rig.model.setPushAvailable(true)
        rig.model.start()
        runCurrent()
        rig.model.openMeet("m1")
        runCurrent()
        assertFalse(rig.model.current.canNotify)
    }

    @Test fun `a follow is stored and sent at the meet's base`() = runTest {
        val rig = Rig(this)
        rig.openWithPush(this)
        assertTrue(rig.model.current.canNotify)
        rig.model.setFollows(MeetFollows(listOf(emma), FollowLead(false, 10)))
        runCurrent()
        val sent = rig.http.followBodies()
        assertEquals(1, sent.size)
        assertEquals("10", sent[0]["lead"]!!.jsonObject["minutes"]!!.jsonPrimitive.content)
        assertEquals("fr", sent[0]["lang"]!!.jsonPrimitive.content)
        assertEquals(listOf(emma), rig.store.get(base, "m1").swimmers)
        assertEquals(base, rig.store.get(base, "m1").base)
    }

    @Test fun `refused keeps the list on the device, and emptying it still stops the server`() = runTest {
        val rig = Rig(this)
        rig.openWithPush(this, PushPermission.REFUSED)
        rig.model.setFollows(MeetFollows(listOf(emma)))
        runCurrent()
        assertTrue(rig.http.followBodies().isEmpty())
        assertEquals(listOf(emma), rig.model.current.meet!!.follows.swimmers)
        rig.model.setFollows(MeetFollows())
        runCurrent()
        assertEquals(0, rig.http.followBodies().single()["swimmers"]!!.jsonArray.size)
    }

    @Test fun `opening a meet re-sends what it follows`() = runTest {
        val store = InMemoryFollowStore()
        store.set(base, "m1", MeetFollows(listOf(emma)))
        val rig = Rig(this, store)
        rig.openWithPush(this)
        assertEquals(1, rig.http.followBodies().size)
    }

    // ── N-11 ──────────────────────────────────────────────────────────────────

    @Test fun `paused sends an empty list and keeps the swimmers`() = runTest {
        val rig = Rig(this)
        rig.openWithPush(this)
        rig.model.setFollows(MeetFollows(listOf(emma)))
        runCurrent()
        rig.model.setFollows(MeetFollows(listOf(emma), enabled = false))
        runCurrent()
        rig.model.setFollows(MeetFollows(listOf(emma)))
        runCurrent()
        assertEquals(listOf(1, 0, 1), rig.http.followBodies().map { it["swimmers"]!!.jsonArray.size })
        assertEquals(listOf(emma), rig.store.get(base, "m1").swimmers)
    }

    @Test fun `paused is sent even without permission`() = runTest {
        val rig = Rig(this)
        rig.openWithPush(this, PushPermission.REFUSED)
        rig.model.setFollows(MeetFollows(listOf(emma), enabled = false))
        runCurrent()
        assertEquals(0, rig.http.followBodies().single()["swimmers"]!!.jsonArray.size)
    }

    @Test fun `a list saved before the pause is on, and a paused one stays paused`() {
        val old = FollowStore.decode(
            """{"k":{"swimmers":[{"name":"Emma Roy","club":"CNQ"}],"by_heats":false,"lead":5,"selected":true}}""",
        )
        assertTrue(old.getValue("k").enabled)
        val paused = mapOf("k" to MeetFollows(listOf(emma), enabled = false))
        assertEquals(paused, FollowStore.decode(FollowStore.encode(paused)))
    }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `a meet held elsewhere is followed, then asked again`() = runTest {
        val rig = Rig(this)
        rig.openWithPush(this)
        rig.http.on("$base/meet/m1/follow", status = 409, body = """{"base":"$base/w2"}""")
        rig.http.on(
            "$base/meet/m1/config",
            body = """{"name":"Meet One","base":"$base/w2","push":["fcm"],"settings":{"num_lanes":6}}""",
        )
        rig.http.on(
            "$base/w2/meet/m1/config",
            body = """{"name":"Meet One","base":"$base/w2","push":["fcm"],"settings":{"num_lanes":6}}""",
        )
        rig.http.on("$base/w2/meet/m1/schedule", body = """{"heats":[]}""")
        rig.http.on("$base/w2/meet/m1/follow", status = 204)
        rig.model.setFollows(MeetFollows(listOf(emma)))
        runCurrent()
        assertEquals(1, rig.http.followBodies("$base/w2/meet/m1/follow").size)
        assertEquals("$base/w2", rig.store.get(base, "m1").base)
    }

    @Test fun `a new token is sent for every meet, and a gone one is dropped`() = runTest {
        val store = InMemoryFollowStore()
        store.set(base, "m1", MeetFollows(listOf(emma)))
        store.set(base, "gone", MeetFollows(listOf(emma)))
        val rig = Rig(this, store)
        rig.http.on("$base/meet/m1/follow", status = 204)
        rig.http.on("$base/meet/gone/follow", status = 404)
        rig.model.setPushPermission(PushPermission.ALLOWED)
        rig.model.setPushToken("new")
        runCurrent()
        assertEquals(1, rig.http.followBodies().size)
        assertEquals(setOf(FollowStore.key(base, "m1")), store.load().keys)
    }

    // ── N-12, N-13 ────────────────────────────────────────────────────────────

    @Test fun `a follow keeps the meet's name and language`() = runTest {
        val rig = Rig(this)
        rig.openWithPush(this)
        rig.model.setFollows(MeetFollows(listOf(emma)))
        runCurrent()
        val kept = rig.store.get(base, "m1")
        assertEquals("Meet One", kept.name)
        assertEquals("fr", kept.lang)
        assertEquals(kept, MeetFollows.fromJson(kept.toJson()))
        assertEquals(listOf("Meet One"), rig.model.current.followedMeets.map { rig.model.current.followedName(it) })
    }

    @Test fun `settings list every followed meet by name, from every server`() = runTest {
        val store = InMemoryFollowStore()
        store.set(base, "m1", MeetFollows(listOf(emma), name = "Zone"))
        store.set("http://pool.local:80", "m2", MeetFollows(listOf(emma), name = "Alpha"))
        val rig = Rig(this, store)
        val meets = rig.model.current.followedMeets
        assertEquals(listOf("Alpha", "Zone"), meets.map { rig.model.current.followedName(it) })
        assertEquals(listOf("pool.local", null), meets.map { rig.model.followedServer(it) })
        assertTrue(Rig(this).model.current.followedMeets.isEmpty())
    }

    @Test fun `pause all sends empty lists and keeps the swimmers`() = runTest {
        val store = InMemoryFollowStore()
        store.set(base, "m1", MeetFollows(listOf(emma)))
        store.set(base, "m3", MeetFollows(listOf(emma), enabled = false)) // paused already: nothing to send
        val rig = Rig(this, store)
        rig.http.on("$base/meet/m1/follow", status = 204)
        rig.model.setPushPermission(PushPermission.REFUSED)
        rig.model.setPushToken("tok")
        runCurrent()
        assertEquals(FollowState.ON, rig.model.current.followState("m1"))
        rig.model.pauseAllFollows()
        runCurrent()
        assertEquals(listOf(0), rig.http.followBodies().map { it["swimmers"]!!.jsonArray.size })
        assertTrue(rig.http.followBodies("$base/meet/m3/follow").isEmpty())
        assertTrue(rig.model.current.followedMeets.all { !it.follows.enabled && it.follows.swimmers == listOf(emma) })
        assertEquals(FollowState.PAUSED, rig.model.current.followState("m1"))
        assertEquals(FollowState.NONE, rig.model.current.followState("m9"))
    }

    @Test fun `a meet turned back on from settings sends its list`() = runTest {
        val store = InMemoryFollowStore()
        store.set(base, "m1", MeetFollows(listOf(emma), enabled = false, lang = "es"))
        val rig = Rig(this, store)
        rig.http.on("$base/meet/m1/follow", status = 204)
        rig.model.setPushPermission(PushPermission.ALLOWED)
        rig.model.setPushToken("tok")
        runCurrent()
        rig.http.puts.clear()
        rig.model.setFollowsEnabled(rig.model.current.followedMeets[0], true)
        runCurrent()
        val body = rig.http.followBodies().single()
        assertEquals(1, body["swimmers"]!!.jsonArray.size)
        assertEquals("es", body["lang"]!!.jsonPrimitive.content)
        assertTrue(rig.model.current.followedMeets[0].follows.enabled)
    }

    // ── N-08, N-09 ────────────────────────────────────────────────────────────

    @Test fun `a gone meet takes its follows with it`() = runTest {
        val store = InMemoryFollowStore()
        store.set(base, "m1", MeetFollows(listOf(emma)))
        val rig = Rig(this, store)
        rig.http.routes()
        rig.http.on("$base/meet/m1/config", status = 404)
        rig.model.start()
        runCurrent()
        rig.model.openMeet("m1")
        runCurrent()
        assertTrue(store.load().isEmpty())
    }

    @Test fun `a tapped notification opens its meet on the schedule, at the heat`() = runTest {
        val rig = Rig(this)
        rig.http.routes()
        rig.model.openFromNotification(HeatFocus("m1", "12", "3"))
        rig.model.start()
        runCurrent()
        val meet = rig.model.current.meet!!
        assertEquals("m1", meet.context.meetId)
        assertEquals(HeatFocus("m1", "12", "3"), meet.focus)
        assertEquals(MeetTab.SCHEDULE, rig.model.current.prefs.tab)
        rig.model.focusShown()
        assertNull(rig.model.current.meet!!.focus)
    }
}
