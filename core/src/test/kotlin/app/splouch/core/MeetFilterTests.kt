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

    @Test fun `facets are all required`() {
        val f = MeetFilter(countries = setOf("CA"), clubs = setOf("CAMO", "Asphalt Green"))
        assertEquals(listOf("mtl"), ids(f.apply(meets)))
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

    @Test fun `dropping a country drops its provinces`() {
        val f = MeetFilter(setOf("CA", "US"), setOf(Province("CA", "QC"), Province("US", "NY"))).toggleCountry("CA")
        assertEquals(setOf("US"), f.countries)
        assertEquals(setOf(Province("US", "NY")), f.provinces)
    }

    @Test fun `options come from the list, sorted as read`() {
        val o = MeetFilter().options(meets, "en")
        assertEquals(listOf("CA", "US"), o.countries)
        assertEquals(listOf("NY", "ON", "QC"), o.provinces.map { it.name })
        assertEquals(listOf("Asphalt Green", "CAMO", "Etobicoke", "Rouge et Or"), o.clubs)
    }

    @Test fun `provinces narrow to the chosen countries`() {
        assertEquals(listOf(Province("US", "NY")), MeetFilter(countries = setOf("US")).options(meets, "en").provinces)
    }

    @Test fun `a chosen value the list no longer holds is still offered`() {
        val o = MeetFilter(countries = setOf("FR"), clubs = setOf("Gone")).options(meets, "en")
        assertTrue("FR" in o.countries)
        assertTrue("Gone" in o.clubs)
    }

    @Test fun `a province is named with its country`() {
        assertEquals("QC, Canada", Province("CA", "QC").label("en"))
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
