package app.splouch.core

import app.splouch.core.board.LapDirection
import app.splouch.core.board.LapSettings
import app.splouch.core.session.MeetTab
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

        // A-11: neither of those servers sent `console`, and neither loses its Results tab.
        assertTrue(c.settings.console.timed)
        assertTrue(p.settings.console.timed)

        // L-23: neither sent `show_laps` either, and the delta column stays a delta column.
        // It is the one display flag that is off by default — not every console's count is
        // exact, so the operator turns it on for a venue where it is.
        assertFalse(c.settings.showLaps)
        assertFalse(p.settings.showLaps)
        assertNull(c.settings.lapDirection)
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.RESULTS, MeetTab.SCHEDULE), MeetTab.of(c))
    }

    @Test fun `A-11 reads console timed, defaults to timed, and never branches on the key`() {
        fun cloud(settings: String) = MeetConfig.fromCloudJson(j("""{"name":"M","settings":$settings}"""))!!
        val manual = cloud("""{"num_lanes":8,"console":{"key":"manual","timed":false}}""")
        assertFalse(manual.settings.console.timed)
        assertEquals("manual", manual.settings.console.key)
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.SCHEDULE), MeetTab.of(manual))

        // A wired console: the key is carried for a support question, and the tab stays.
        val wired = cloud("""{"num_lanes":8,"console":{"key":"cts_gen6","timed":true}}""")
        assertEquals("cts_gen6", wired.settings.console.key)
        assertEquals(3, MeetTab.of(wired).size)

        // The key is never the test. A local plugin driven by hand loses the tab although
        // its key is not "manual"; one named "manual_backup" that times keeps it.
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.SCHEDULE),
            MeetTab.of(cloud("""{"console":{"key":"club_plugin","timed":false}}""")))
        assertEquals(3, MeetTab.of(cloud("""{"console":{"key":"manual_backup","timed":true}}""")).size)

        // Absent, empty, malformed, or a type nobody promised — all of it reads as timed,
        // because the tab is only ever taken away on the server saying so in as many words.
        listOf("""{"num_lanes":8}""", """{"console":{}}""", """{"console":null}""",
               """{"console":"manual"}""", """{"console":[]}""", """{"console":{"timed":"maybe"}}""")
            .forEach { assertTrue(cloud(it).settings.console.timed, "expected timed for $it") }

        // "false" as a string is what a lenient server sends, and it still means false.
        assertFalse(cloud("""{"console":{"key":"manual","timed":"false"}}""").settings.console.timed)

        // The Pi carries the same block one level up (api.md §6).
        val pi = MeetConfig.fromPiJson(j("""{"meet_title":"Pool","num_lanes":6,"console":{"key":"manual","timed":false}}"""))!!
        assertEquals(listOf(MeetTab.SCOREBOARD, MeetTab.SCHEDULE), MeetTab.of(pi))
        assertTrue(MeetConfig.fromPiJson(j("""{"meet_title":"Pool"}"""))!!.settings.console.timed)
    }

    @Test fun `A-04 stores a tab as a choice, not as a number`() {
        assertEquals(MeetTab.SCHEDULE, MeetTab.parse("SCHEDULE"))
        assertEquals(MeetTab.RESULTS, MeetTab.parse("results"))
        assertNull(MeetTab.parse("2"))
        assertNull(MeetTab.parse(null))
        assertNull(MeetTab.parse(""))
        // Schedule is page 2 of a timed meet and page 1 of an untimed one; the identity is
        // what survives the change, which is the whole reason it is stored as one.
        val untimed = MeetConfig.fromCloudJson(j("""{"name":"M","settings":{"console":{"key":"manual","timed":false}}}"""))!!
        assertEquals(1, MeetTab.of(untimed).indexOf(MeetTab.SCHEDULE))
        assertEquals(-1, MeetTab.of(untimed).indexOf(MeetTab.RESULTS))
    }

    @Test fun `meet list, i18n`() {
        val m = MeetSummary.listFromJson(j("""{"meets":[{"id":"a1","name":"M","offline":true,"has_picker_image":false},{"name":"no id"}]}"""))
        assertEquals(1, m.size)
        assertTrue(m[0].offline)
        val b = I18nBundle.fromJson(j("""{"lang":"fr","mobile":{"scoreboard":"Tableau"},"display":{},"labels":{"short":{"event":"ÉP"},"long":{"event":"ÉPREUVE"}},"event_name":{"unit":"m"}}"""))!!
        assertEquals("Tableau", b.mobile["scoreboard"])
        assertEquals("ÉPREUVE", b.labels["long"]!!["event"])
    }

    @Test fun `L-23 show_laps is off unless the meet says so, and anything but down counts up`() {
        fun cloud(settings: String) = MeetConfig.fromCloudJson(j("""{"name":"M","settings":$settings}"""))!!.settings
        val on = cloud("""{"show_laps":true,"lap_direction":"Down"}""")
        assertTrue(on.showLaps)
        assertEquals(LapDirection.DOWN, LapSettings.from(on).direction)
        assertTrue(LapSettings.from(on).show)

        // `"up"`, an unrecognised word, an absent field and a server older than any of it all
        // count up: it is the direction that needs nothing but the console.
        listOf("""{"show_laps":true,"lap_direction":"up"}""", """{"show_laps":true,"lap_direction":"sideways"}""",
               """{"show_laps":true,"lap_direction":""}""", """{"show_laps":true}""")
            .forEach { assertEquals(LapDirection.UP, LapSettings.from(cloud(it)).direction, "expected up for $it") }

        // And a direction without the flag draws nothing at all — the flag is the only gate,
        // so which way an unlit column would have counted never comes up.
        assertFalse(LapSettings.from(cloud("""{"lap_direction":"down"}""")).show)
        // A value nobody promised is not a reason to turn the column into lengths.
        listOf("""{"show_laps":"yes"}""", """{"show_laps":1}""", """{"show_laps":null}""")
            .forEach { assertFalse(cloud(it).showLaps, "expected off for $it") }
        // The Pi sends the same keys flat.
        assertTrue(MeetConfig.fromPiJson(j("""{"meet_title":"Pool","show_laps":true}"""))!!.settings.showLaps)
    }

    @Test fun `L-23 the frame's venue numbers and lane counts decode tolerantly`() {
        val f = ScoreboardFrame.fromJson(j("""{"expected_splits":"8","split_step":2.0,"lane_splits1":6,"lane_splits2":"x","lane_splits3":-2}"""))!!
        assertEquals(8, f.expectedSplits)
        assertEquals(2, f.splitStep)
        assertEquals(6, f.laneSplits(1))
        // Present but undecodable is the same nothing the server sends at the top of a heat.
        assertEquals(0, f.laneSplits(2))
        assertEquals(0, f.laneSplits(3))
        // Absent is not 0: the board keeps what it has, because frames are partial (L-10).
        assertNull(f.laneSplits(4))
        val empty = ScoreboardFrame.fromJson(j("""{}"""))!!
        assertNull(empty.expectedSplits)
        assertNull(empty.splitStep)
        // A `split_step` of 0 would fire the final-stretch test a length early.
        assertEquals(1, ScoreboardFrame.fromJson(j("""{"split_step":0}"""))!!.splitStep)
    }
}
