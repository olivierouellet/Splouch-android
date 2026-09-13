package app.splouch.core

import app.splouch.core.schedule.SearchFold.fold
import app.splouch.core.schedule.Suggestion
import app.splouch.core.schedule.SuggestionIndex
import app.splouch.core.schedule.SuggestionType
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.parseJsonOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** app.md S-09: the fold, and the index built from the S-01 payload with no request. */
class SuggestionIndexTests {

    @Test fun `the fold expands the letters NFD leaves whole`() {
        // The 17-row table: dropping every codepoint > U+007F without it would punch holes.
        assertEquals("ile-des-soeurs", fold("Île-des-Sœurs"))
        assertEquals("strasse", fold("Straße"))
        assertEquals("sorensen", fold("Sørensen"))
        assertEquals("aevar", fold("Ævar"))
        assertEquals("oyvind", fold("Ǿyvind"))       // ǿ → ø + acute, then the ø row
        assertEquals("thor", fold("Þór"))
        assertEquals("dorde", fold("Ðorđe"))
        assertEquals("lukasz", fold("Łukasz"))
        assertEquals("jose garcia", fold("José García"))
        assertEquals("muller", fold("Müller"))
        assertEquals("elise", fold("Élise"))
        // A name with no ASCII at all is unreachable by search — a known limit of S-09.
        assertEquals("", fold("北島"))
    }

    @Test fun `each spelling folds to the same string as its plain form`() {
        // The property that matters: what the spectator types reaches what the meet file spells.
        for ((accented, plain) in listOf(
            "Île-des-Sœurs" to "ile-des-soeurs", "Sørensen" to "sorensen", "Ǿyvind" to "oyvind",
            "José García" to "jose garcia", "Straße" to "strasse", "Ævar" to "aevar",
            "Þór" to "thor", "Ðorđe" to "dorde", "Łukasz" to "lukasz", "Müller" to "muller",
        )) {
            assertEquals(fold(plain), fold(accented), "$accented and $plain must fold alike")
        }
        assertEquals(fold("soeurs"), fold("Sœurs"))
        assertEquals(1, setOf(fold("ELISE"), fold("Élise"), fold("élise")).size)
    }

    private val heats: List<ScheduleHeat> = ScheduleHeat.listFromJson(parseJsonOrNull("""{"heats":[
        {"event":1,"heat":1,"event_name":"a","time":"","lanes":[
            {"lane":1,"name":"Élise Côté","club":"Île-des-Sœurs","seed_time":"","swimmers":[]},
            {"lane":2,"name":"Bo Xu","club":"Rays","seed_time":"","swimmers":[]}]},
        {"event":2,"heat":1,"event_name":"b","time":"","lanes":[
            {"lane":3,"name":"Sharks Relay A","club":"Sharks","seed_time":"","swimmers":[
                {"name":"Ann Lee","first":"Ann"},{"name":"Sørensen Þór","first":"Þór"}]}]},
        {"event":3,"heat":1,"event_name":"c","time":"","lanes":[
            {"lane":1,"name":"Bo Xu","club":"Sharks","seed_time":"","swimmers":[]}]}
    ]}""")) ?: error("fixture is not a schedule")

    private val index = SuggestionIndex.from(heats)

    @Test fun `a relay is findable by its team name and by a member's name`() {
        // A spectator may know the team and not one swimmer on it, so the lane's own name indexes.
        assertEquals(listOf(Suggestion(SuggestionType.SWIMMER, "Sharks Relay A", "Sharks")), index.search("relay a"))
        // S-14 stays a separate concern: the member is its own suggestion.
        assertEquals(listOf(Suggestion(SuggestionType.SWIMMER, "Ann Lee", "Sharks")), index.search("ann"))
        assertEquals(listOf(Suggestion(SuggestionType.SWIMMER, "Sørensen Þór", "Sharks")), index.search("sorensen th"))
    }

    @Test fun `search is case- and accent-insensitive both ways`() {
        val elise = Suggestion(SuggestionType.SWIMMER, "Élise Côté", "Île-des-Sœurs")
        assertEquals(listOf(elise), index.search("elise"))
        assertEquals(listOf(elise), index.search("ÉLISE"))
        assertEquals(listOf(elise), index.search("côté"))
        assertEquals(listOf(Suggestion(SuggestionType.CLUB, "Île-des-Sœurs", "")), index.search("des-soeurs"))
    }

    @Test fun `swimmers before clubs, each by name, and an empty query suggests nothing`() {
        val s = index.search("s")
        assertEquals(
            listOf("Élise Côté", "Sharks Relay A", "Sørensen Þór", "Île-des-Sœurs", "Rays", "Sharks"),
            s.map { it.name },
        )
        assertEquals(3, s.count { it.type == SuggestionType.SWIMMER })
        assertEquals(listOf(SuggestionType.CLUB, SuggestionType.CLUB, SuggestionType.CLUB), s.drop(3).map { it.type })
        // S-19: an empty or whitespace-only query is not "show me everything".
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
        // Nor is a query that folds away entirely.
        assertTrue(index.search("北島").isEmpty())
        assertTrue(index.search("zz").isEmpty())
        assertTrue(SuggestionIndex.from(emptyList()).search("a").isEmpty())
        assertTrue(SuggestionIndex.from(null).search("a").isEmpty())
    }

    @Test fun `one entry per name, the later lane's club winning, and capped at 20`() {
        // "Bo Xu" swims for Rays in event 1 and Sharks in event 3: the later lane wins.
        assertEquals(listOf(Suggestion(SuggestionType.SWIMMER, "Bo Xu", "Sharks")), index.search("bo xu"))
        assertEquals(1, index.search("sharks").count { it.type == SuggestionType.CLUB })

        val many = ScheduleHeat.listFromJson(parseJsonOrNull("""{"heats":[{"event":1,"heat":1,"event_name":"a","time":"","lanes":[""" +
            (1..30).joinToString(",") { """{"lane":$it,"name":"Swimmer ${"%02d".format(it)}","club":"Club $it","seed_time":"","swimmers":[]}""" } +
            """]}]}"""))!!
        val all = SuggestionIndex.from(many).search("swimmer")
        assertEquals(SuggestionIndex.MAX, all.size)
        assertEquals("Swimmer 01", all.first().name)
        assertEquals("Swimmer 20", all.last().name)
    }
}
