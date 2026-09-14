package app.splouch.core

import app.splouch.core.schedule.Filter
import app.splouch.core.schedule.HeatRef
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.schedule.SuggestionType
import app.splouch.core.strings.EventName
import app.splouch.core.strings.Labels
import app.splouch.core.strings.StringTable
import app.splouch.core.theme.Theme
import app.splouch.core.wire.EventNameParts
import app.splouch.core.wire.I18nBundle
import app.splouch.core.wire.MeetSettings
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringsThemeScheduleTests {
    private val en = I18nBundle("en", mapOf("scoreboard" to "Scoreboard", "results" to "Results", "only_en" to "English only"), emptyMap(),
        mapOf("short" to mapOf("event" to "EV", "heat" to "HT", "lane" to "LN"), "long" to mapOf("event" to "EVENT", "heat" to "HEAT", "lane" to "LN")),
        mapOf("unit" to "m", "separator" to "  —  ", "backstroke" to "Backstroke", "girls" to "Girls", "relay" to "Relay"))
    private val frBuiltIn = I18nBundle("fr", mapOf("scoreboard" to "Tableau (built-in)", "results" to "Résultats"), emptyMap(),
        mapOf("short" to mapOf("event" to "ÉP", "heat" to "SÉR", "lane" to "CL"), "long" to mapOf("event" to "ÉPREUVE", "heat" to "SÉRIE", "lane" to "CL")),
        mapOf("unit" to "m", "separator" to "  —  ", "backstroke" to "dos", "girls" to "Filles"))
    private val frCached = I18nBundle("fr", mapOf("scoreboard" to "Tableau"), emptyMap(), emptyMap(), emptyMap())

    @Test fun `resolution order - cached, built-in, built-in English, the key`() {
        val t = StringTable("fr", frCached, frBuiltIn, en)
        assertEquals("Tableau", t.mobile("scoreboard"))
        assertEquals("Résultats", t.mobile("results"))
        assertEquals("English only", t.mobile("only_en"))
        assertEquals("never_defined", t.mobile("never_defined"))
        assertEquals("ÉPREUVE", t.labels("long")["event"])
        assertEquals("dos", t.eventVocab["backstroke"])
        assertEquals("Relay", t.eventVocab["relay"])   // English floor per key
    }

    @Test fun `labels - the operator's as sent, with EVENT and HEAT following the device's style`() {
        val t = StringTable("fr", null, frBuiltIn, en)
        val settings = MeetSettings(numLanes = 8, locale = "fr", labels = mapOf("event" to "OP", "lane" to "OPLN"), labelStyle = "short")
        // Untouched: the operator's words stand, except the two headers the control owns —
        // long, whatever the operator's own label_style says.
        val default = Labels.resolve(settings, null, Labels.DEFAULT, t)
        assertEquals("ÉPREUVE", default["event"])
        assertEquals("SÉRIE", default["heat"])
        assertEquals("OPLN", default["lane"])     // every other column is the operator's, as sent
        val short = Labels.resolve(settings, null, "short", t)
        assertEquals("ÉP", short["event"])
        assertEquals("OPLN", short["lane"])
        // A language choice replaces the base too: that language's table, short columns.
        val fr = Labels.resolve(settings, "fr", "short", t)
        assertEquals("ÉP", fr["event"])
        assertEquals("CL", fr["lane"])            // narrow columns keep their short word
        assertEquals("ÉPREUVE", Labels.resolve(settings, "fr", "long", t)["event"])
    }

    @Test fun `event name composes from parts and falls back`() {
        val vocab = StringTable("fr", null, frBuiltIn, en).eventVocab
        val parts = EventNameParts(raw = "200 Backstroke Girls 12 & Under", dist = "200", stroke = "backstroke", gender = "girls", age = "< 12")
        assertEquals("200 m dos  —  Filles < 12", EventName.compose(parts, vocab))
        assertEquals("Club Handicap Final", EventName.compose(EventNameParts(raw = "Club Handicap Final"), vocab))
        assertNull(EventName.compose(EventNameParts(), vocab))
        assertNull(EventName.compose(null, vocab))
        assertEquals("fallback", EventName.display("fallback", EventNameParts(), vocab))
        assertEquals("4x50 m Backstroke Relay", EventName.compose(EventNameParts(dist = "4x50", stroke = "backstroke", relay = true), en.eventName))
    }

    @Test fun `theme falls back to the documented defaults`() {
        val t = Theme(mapOf("bg" to "#000000", "time" to ""), emptyMap())
        assertEquals("#000000", t.color("bg"))
        assertEquals("#FFD700", t.color("time"))
        assertEquals("#3b9eff", t.color("schedule_event"))
        assertEquals("DSEG7Classic", t.font("digits"))
        assertEquals(0xFF0d0d0dL, Theme.parseArgb("#0d0d0d"))
        assertEquals(0xFF112233L, Theme.parseArgb("#123"))
        assertNull(Theme.parseArgb("red"))
    }

    private val heats = ScheduleHeat.listFromJson(parseJsonOrNull("""{"heats":[
        {"event":1,"heat":1,"event_name":"a","time":"","lanes":[{"lane":1,"name":"Ann Lee","club":"Sharks","seed_time":"","swimmers":[]},{"lane":2,"name":"Bo Xu","club":"Rays","seed_time":"","swimmers":[]}]},
        {"event":1,"heat":2,"event_name":"a","time":"","lanes":[{"lane":1,"name":"Cy Oh","club":"Rays","seed_time":"","swimmers":[]}]},
        {"event":2,"heat":1,"event_name":"b","time":"","lanes":[{"lane":3,"name":"Relay A","club":"Sharks","seed_time":"","swimmers":[{"name":"Ann Lee","first":"Ann"},{"name":"Di Fu","first":"Di"}]}]},
        {"event":3,"heat":1,"event_name":"c","time":"","lanes":[]}
    ]}"""))!!

    @Test fun `filters are OR-ed and match relay members, heats with no match disappear`() {
        val st = ScheduleFilterState(filters = listOf(Filter(SuggestionType.SWIMMER, "Ann Lee")))
        val v = ScheduleFilter.visible(heats, st, null)
        assertEquals(listOf("1/1", "2/1"), v.map { "${it.heat.event}/${it.heat.heat}" })
        assertEquals(listOf(0, 1), v.map { it.index })
        assertEquals(1, v[0].lanes.size)
        val both = st.add(Filter(SuggestionType.CLUB, "Rays"))
        assertEquals(3, ScheduleFilter.visible(heats, both, null).size)
        assertEquals("Ann · Di", ScheduleFilter.displayName(heats[2].lanes[0]))
    }

    @Test fun `all heats keeps every heat, upcoming cuts by position and changes nothing when unknown`() {
        val st = ScheduleFilterState(filters = listOf(Filter(SuggestionType.CLUB, "Rays")), allHeats = true)
        val v = ScheduleFilter.visible(heats, st, HeatRef("2", "1"))
        assertEquals(4, v.size)
        assertEquals(0, v[2].lanes.size)
        assertTrue(v[2].isCurrent)
        val up = ScheduleFilterState(upcomingOnly = true)
        assertEquals(listOf("2/1", "3/1"), ScheduleFilter.visible(heats, up, HeatRef("2", "1")).map { "${it.heat.event}/${it.heat.heat}" })
        assertEquals(4, ScheduleFilter.visible(heats, up, null).size)
        assertEquals(4, ScheduleFilter.visible(heats, up, HeatRef("7", "7")).size)
    }

    @Test fun `empty states and retained filters across a re-fetch`() {
        val none = ScheduleFilterState(filters = listOf(Filter(SuggestionType.CLUB, "Nobody")))
        assertEquals(app.splouch.core.schedule.EmptyState.NO_MATCHES, ScheduleFilter.emptyState(heats, ScheduleFilter.visible(heats, none, null), none))
        assertEquals(app.splouch.core.schedule.EmptyState.NO_SCHEDULE, ScheduleFilter.emptyState(emptyList(), emptyList(), ScheduleFilterState()))
        val kept = ScheduleFilter.retain(none.add(Filter(SuggestionType.SWIMMER, "Di Fu")), heats)
        assertEquals(listOf(Filter(SuggestionType.SWIMMER, "Di Fu")), kept.filters)
    }
}
