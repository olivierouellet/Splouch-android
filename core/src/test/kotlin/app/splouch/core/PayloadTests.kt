package app.splouch.core

import app.splouch.core.wire.I18nBundle
import app.splouch.core.wire.MeetConfig
import app.splouch.core.wire.MeetSummary
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ScoreboardFrame
import app.splouch.core.wire.ServerInfo
import app.splouch.core.wire.ServerKind
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayloadTests {
    private fun j(s: String) = parseJsonOrNull(s)

    @Test fun `server handshake`() {
        val s = ServerInfo.fromJson(j("""{"kind":"pi","name":"Piscine","contract":{"api":"v2","app":"v1"}}"""))!!
        assertEquals(ServerKind.PI, s.kind)
        assertEquals("v2", s.contract.api)
        assertNull(ServerInfo.fromJson(j("""{"kind":"tv"}""")))
        assertNull(ServerInfo.fromJson(j("""{"meets":[]}""")))
    }

    @Test fun `scoreboard frame is partial and event heat read as trimmed strings`() {
        val f = ScoreboardFrame.fromJson(j("""{"current_event":" 3","current_heat":1,"lane_running4":true,"lane_running5":"false","lane_time4":"25.61","lane_delta_seconds4":-0.46,"lane_delta_better4":true,"lane_name_alt4":"A · B"}"""))!!
        assertEquals("3", f.currentEvent)
        assertEquals("1", f.currentHeat)
        assertEquals(mapOf(4 to true, 5 to false), f.runningEdges())
        assertEquals("25.61", f.laneTime(4))
        assertEquals(-0.46, f.laneDeltaSeconds(4))
        assertEquals(true, f.laneDeltaBetter(4))
        assertNull(f.laneName(4))
        assertFalse(f.carriesNames())
        assertNull(f.runningTime)
    }

    @Test fun `event_name_parts null is present-but-null`() {
        val f = ScoreboardFrame.fromJson(j("""{"event_name_parts":null}"""))!!
        assertTrue(f.hasEventNameParts)
        assertNull(f.eventNameParts)
        val g = ScoreboardFrame.fromJson(j("""{"event_name_parts":{"dist":"200","stroke":"backstroke","relay":false,"gender":"girls","age":"< 12"}}"""))!!
        assertEquals("backstroke", g.eventNameParts!!.stroke)
    }

    @Test fun `results snapshot, sort absent`() {
        val s = ResultsSnapshot.fromJson(j("""{"event":3,"heat":"1 ","event_name":"x","lanes":[{"channel":4,"place":"1","time":"2:20.92","name":"n","club":"c","alt":"","delta_seconds":null,"delta_better":null}]}"""))!!
        assertEquals("3", s.event)
        assertEquals("1", s.heat)
        assertNull(s.sort)
        assertTrue(s.isLaneSort)
        assertEquals(4, s.lanes[0].channel)
        assertNull(s.lanes[0].deltaSeconds)
        assertFalse(ResultsSnapshot.fromJson(j("""{"sort":"place","lanes":[]}"""))!!.isLaneSort)
    }

    @Test fun `schedule heats normalise integers to strings and tolerate empty`() {
        val heats = ScheduleHeat.listFromJson(j("""{"heats":[{"event":3,"heat":1,"event_name":"e","time":"10:42","lanes":[{"lane":4,"name":"Relay A","club":"C","seed_time":"1:00.00","swimmers":[{"name":"Ann Lee","first":"Ann"},{"name":"Bo Xu"}]}]}]}"""))!!
        assertEquals("3", heats[0].event)
        assertEquals("1", heats[0].heat)
        assertEquals("Ann", heats[0].lanes[0].swimmers[0].display)
        assertEquals("Bo Xu", heats[0].lanes[0].swimmers[1].display)
        assertEquals(emptyList(), ScheduleHeat.listFromJson(j("""{"heats":[]}""")))
        assertNull(ScheduleHeat.listFromJson(j("""{"detail":"Not Found"}""")))
    }

    @Test fun `meet config from cloud and from pi`() {
        val c = MeetConfig.fromCloudJson(j("""{"name":"Meet","app_window_title":"","live":false,"settings":{"num_lanes":"10","show_club":false,"locale":"fr","labels":{"event":"ÉP"},"label_style":"long","theme_colors":{"bg":"#000"}}}"""))!!
        assertEquals("Meet", c.title)
        assertEquals(false, c.live)
        assertEquals(10, c.settings.numLanes)
        assertFalse(c.settings.showClub)
        assertTrue(c.settings.showName)
        assertEquals("fr", c.settings.locale)
        assertEquals("long", c.settings.labelStyle)
        val p = MeetConfig.fromPiJson(j("""{"meet_title":"Pool","num_lanes":6,"labels":{"event":"EV"},"locale":"en","theme_fonts":{"digits":"DSEG14Classic"}}"""))!!
        assertEquals("Pool", p.title)
        assertNull(p.live)
        assertEquals(6, p.settings.numLanes)
        assertNull(p.settings.labelStyle)
        assertEquals("DSEG14Classic", p.settings.themeFonts["digits"])
    }

    @Test fun `meet list, i18n`() {
        val m = MeetSummary.listFromJson(j("""{"meets":[{"id":"a1","name":"M","offline":true,"has_picker_image":false},{"name":"no id"}]}"""))
        assertEquals(1, m.size)
        assertTrue(m[0].offline)
        val b = I18nBundle.fromJson(j("""{"lang":"fr","mobile":{"scoreboard":"Tableau"},"display":{},"labels":{"short":{"event":"ÉP"},"long":{"event":"ÉPREUVE"}},"event_name":{"unit":"m"}}"""))!!
        assertEquals("Tableau", b.mobile["scoreboard"])
        assertEquals("ÉPREUVE", b.labels["long"]!!["event"])
    }
}
