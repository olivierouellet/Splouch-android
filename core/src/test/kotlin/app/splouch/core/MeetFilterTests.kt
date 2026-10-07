package app.splouch.core

import app.splouch.core.session.MeetFilter
import app.splouch.core.session.MeetFilter.Province
import app.splouch.core.session.PickerState
import app.splouch.core.wire.MeetSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-21: the picker's filter by country, state/province and club, remembered. */
class MeetFilterTests {
    private fun meet(id: String, country: String = "", province: String = "", organizer: String = "") =
        MeetSummary(id, id, "", "", organizer, "", offline = false, hasPickerImage = false, country, province)

    private val meets = listOf(
        meet("mtl", "CA", "QC", "CAMO"),
        meet("qc", "CA", "QC", "Rouge et Or"),
        meet("tor", "CA", "ON", "Etobicoke"),
        meet("nyc", "US", "NY", "Asphalt Green"),
        meet("bare"),
    )

    private fun ids(list: List<MeetSummary>) = list.map { it.id }

    @Test fun `an empty filter leaves every meet in order`() {
        assertFalse(MeetFilter().isActive)
        assertEquals(listOf("mtl", "qc", "tor", "nyc", "bare"), ids(MeetFilter().apply(meets)))
    }

    @Test fun `values of one facet are alternatives`() {
        assertEquals(listOf("mtl", "tor"), ids(MeetFilter(clubs = setOf("CAMO", "Etobicoke")).apply(meets)))
    }

    /** A country, a province and a club: a meet holding any one of them shows. */
    @Test fun `facets are alternatives too`() {
        val f = MeetFilter(setOf("US"), setOf(Province("CA", "ON")), setOf("CAMO"))
        assertEquals(listOf("mtl", "tor", "nyc"), ids(f.apply(meets)))
    }

    @Test fun `an empty field fails an active facet`() {
        assertFalse(MeetFilter(countries = setOf("CA")).matches(meet("bare")))
        assertFalse(MeetFilter(provinces = setOf(Province("", ""))).matches(meet("bare")))
        assertFalse(MeetFilter(clubs = setOf("")).matches(meet("bare")))
    }

    @Test fun `clubs and provinces are compared folded`() {
        assertTrue(MeetFilter(clubs = setOf("rouge ET or")).matches(meets[1]))
        assertTrue(MeetFilter(provinces = setOf(Province("ca", "qc"))).matches(meets[0]))
        assertTrue(MeetFilter(countries = setOf("CA")).matches(meet("x", "ca")))
    }

    @Test fun `a province is only one with its country`() {
        assertFalse(MeetFilter(provinces = setOf(Province("US", "QC"))).matches(meets[0]))
    }

    @Test fun `toggling a club twice leaves no filter`() {
        val once = MeetFilter().toggleClub("CAMO")
        assertTrue(once.hasClub("camo"))
        assertFalse(once.toggleClub("camo").isActive)
    }

    /** Provinces are choices of their own: dropping their country keeps them. */
    @Test fun `dropping a country keeps its provinces`() {
        val f = MeetFilter(setOf("CA"), setOf(Province("CA", "QC"))).toggleCountry("CA")
        assertTrue(f.countries.isEmpty())
        assertEquals(setOf(Province("CA", "QC")), f.provinces)
    }

    /** Typed letters kept upper-cased, without spaces or symbols. */
    @Test fun `a typed club is kept as its official letters`() {
        assertEquals("CAMO", MeetFilter.clubLetters("  c.a.m.o "))
        assertEquals("ROUGEETOR", MeetFilter.clubLetters("Rouge-et-Or!"))
        val f = MeetFilter().addClubLetters(" camo ").addClubLetters("C A M O").addClubLetters(" .- ")
        assertEquals(setOf("CAMO"), f.clubs)
        assertEquals(listOf("mtl"), ids(f.apply(meets)))
    }

    /** `C.A.M.O.` on the meet is the `CAMO` chosen. */
    @Test fun `clubs match by letters and digits only`() {
        assertTrue(MeetFilter(clubs = setOf("CAMO")).matches(meet("x", organizer = "C.A.M.O.")))
        assertFalse(MeetFilter(clubs = setOf("CAMO")).matches(meet("x", organizer = "CAMOX")))
    }

    /** Clubs from the list; every country and province the app knows, whether or not a meet of the list is there. */
    @Test fun `options offer every known region, sorted as read`() {
        val o = MeetFilter().options(meets, "en")
        assertEquals(listOf("CA", "MX", "US"), o.countries)
        assertEquals(13 + 32 + 56, o.provinces.size)
        assertEquals("Aguascalientes, Mexico", o.provinces.first().label("en"))
        assertEquals(listOf("Asphalt Green", "CAMO", "Etobicoke", "Rouge et Or"), o.clubs)
    }

    /** Any province may widen what a country lets through, so all stay offered. */
    @Test fun `provinces stay offered whatever the countries`() {
        assertEquals(13 + 32 + 56, MeetFilter(countries = setOf("CA")).options(meets, "en").provinces.size)
    }

    /** A region the app does not know is offered once the list holds it. */
    @Test fun `an unknown region the list holds is offered`() {
        val o = MeetFilter().options(listOf(meet("muc", "DE", "BY")), "en")
        assertTrue("DE" in o.countries)
        assertTrue(Province("DE", "BY") in o.provinces)
    }

    @Test fun `a chosen value the list no longer holds is still offered`() {
        val o = MeetFilter(countries = setOf("FR"), clubs = setOf("Gone")).options(meets, "en")
        assertTrue("FR" in o.countries)
        assertTrue("Gone" in o.clubs)
    }

    @Test fun `a province is named in full with its country`() {
        assertEquals("Québec, Canada", Province("CA", "QC").label("en"))
        assertEquals("BY, Germany", Province("DE", "BY").label("en"))
    }

    @Test fun `spellings of one known province are one choice`() {
        val spelled = listOf(meet("a", "CA", "QC"), meet("b", "CA", "Québec"), meet("c", "CA", "quebec"))
        val quebec = MeetFilter().options(spelled, "en").provinces.filter { it.label("en") == "Québec, Canada" }
        assertEquals(1, quebec.size)
        assertEquals(3, MeetFilter(provinces = setOf(quebec[0])).apply(spelled).size)
    }

    // ── remembered ─────────────────────────────────────────────────────────────

    @Test fun `the filter survives an encode and decode`() {
        val f = MeetFilter(setOf("CA"), setOf(Province("CA", "QC")), setOf("CAMO"))
        assertEquals(f, MeetFilter.decode(MeetFilter.encode(f)))
    }

    @Test fun `nothing stored, or something unreadable, is no filter`() {
        assertFalse(MeetFilter.decode(null).isActive)
        assertFalse(MeetFilter.decode("""{"countries":3}""").isActive)
        assertFalse(MeetFilter.decode("not json").isActive)
    }

    // ── on the picker ──────────────────────────────────────────────────────────

    @Test fun `the query searches what the filter leaves`() {
        val p = PickerState(meets = meets, filter = MeetFilter(countries = setOf("CA")), searching = true, query = "o")
        assertEquals(listOf("tor"), ids(p.copy(query = "etob").shownMeets))
        assertEquals(2, p.hiddenByFilter)
    }

    @Test fun `the filter is offered with search, and whenever one stands`() {
        assertFalse(PickerState(meets = meets.take(2)).canFilter)
        assertTrue(PickerState(meets = meets.take(2), filter = MeetFilter(clubs = setOf("CAMO"))).canFilter)
        assertTrue(PickerState(meets = meets).canFilter)
    }

    /** P-18 counts every meet listed, so a filter never flips cards to rows and back. */
    @Test fun `the compact shape counts every meet, not the filtered ones`() {
        val many = List(11) { meet("m$it", "CA") } + meet("us", "US")
        assertTrue(PickerState(meets = many, filter = MeetFilter(countries = setOf("US"))).compact)
    }
}
