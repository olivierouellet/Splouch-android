package app.splouch.core

import app.splouch.core.session.MeetSearch
import app.splouch.core.session.PickerState
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.countryName
import app.splouch.core.session.region
import app.splouch.core.wire.MeetSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetSearchTests {
    private val card = "Coupe du Québec 2026-10-04 Île-des-Sœurs Swimming CAMO"

    private fun meet(id: String, name: String = "", location: String = "", organizer: String = "", date: String = "") =
        MeetSummary(id, name, location, sport = "", organizer, date, offline = false, hasPickerImage = false)

    @Test fun `every word must be in the card, in any order, folded`() {
        assertTrue(MeetSearch.matches(card, "quebec 2026"))
        assertTrue(MeetSearch.matches(card, "2026 coupe"))
        assertFalse(MeetSearch.matches(card, "quebec 2025"))
        assertTrue(MeetSearch.matches(card, "QUÉBEC"))
        assertTrue(MeetSearch.matches(card, "soeurs"))
        assertTrue(MeetSearch.matches(card, "camo"))
    }

    @Test fun `an empty or blank query matches`() {
        assertTrue(MeetSearch.matches(card, ""))
        assertTrue(MeetSearch.matches(card, "   "))
    }

    @Test fun `the search text joins the fields with single spaces, skipping empty ones, organizer included`() {
        val m = MeetSummary(
            "m",
            "Coupe du Québec",
            "Île-des-Sœurs",
            "Swimming",
            "CAMO",
            "2026-10-04",
            offline = false,
            hasPickerImage = false,
        )
        assertEquals(card, MeetSearch.text(m))
        assertEquals("Solo", MeetSearch.text(meet("s", name = "Solo")))
    }

    @Test fun `the field appears at 3 meets and not at 2`() {
        assertFalse(PickerState(meets = List(2) { meet("m$it") }).canSearch)
        assertTrue(PickerState(meets = List(3) { meet("m$it") }).canSearch)
    }

    @Test fun `filtering keeps the server's order, and applies only while search is open`() {
        val meets = listOf(
            meet("a", name = "Winter Open", location = "Laval"),
            meet("b", name = "Coupe", organizer = "CAMO"),
            meet("c", name = "Open d'été", location = "Laval"),
        )
        val open = PickerState(meets = meets, searching = true, query = "laval")
        assertEquals(listOf("a", "c"), open.shownMeets.map { it.id })
        assertEquals(listOf("b"), open.copy(query = "camo").shownMeets.map { it.id })
        assertEquals(listOf("a", "b", "c"), open.copy(searching = false).shownMeets.map { it.id })
        // A refresh that leaves too few meets closes the field, and the query no longer hides any.
        val shrunk = open.copy(meets = listOf(meets[0], meets[2]), query = "winter")
        assertEquals(listOf("a", "c"), shrunk.shownMeets.map { it.id })
    }

    // ── app.md v3: P-01, P-17, P-18 ───────────────────────────────────────────

    private fun located(id: String, name: String, country: String, province: String) = MeetSummary(
        id,
        name,
        "",
        "",
        "",
        "",
        offline = false,
        hasPickerImage = false,
        country = country,
        province = province,
    )

    @Test fun `P-17 searches the province, the country code, and the country's name in the reader's language`() {
        val meets = listOf(
            located("qc", "Coupe", country = "CA", province = "QC"),
            located("ge", "Open", country = "CH", province = "GE"),
            located("none", "Gala", country = "", province = ""),
        )
        fun shown(query: String, lang: String) =
            PickerState(meets = meets, searching = true, query = query, lang = lang).shownMeets.map { it.id }
        assertEquals(listOf("qc"), shown("qc", "en"))
        assertEquals(listOf("ge"), shown("ch", "en"))
        assertEquals(listOf("ge"), shown("switzerland", "en"))
        assertEquals(listOf("ge"), shown("suisse", "fr"))
        assertEquals(listOf("ge"), shown("suiza", "es"))
        // the reader's language, not every language
        assertEquals(emptyList(), shown("suisse", "en"))
        assertEquals(listOf("qc"), shown("canada coupe", "fr"))
    }

    @Test fun `P-01 the region reads province then country, named in the reader's language`() {
        assertEquals("Allemagne", countryName("DE", "fr"))
        assertEquals("Germany", countryName("de", "en"))
        assertEquals("", countryName("", "en"))
        assertEquals("C1", countryName("C1", "en"))
        assertEquals("Québec, Canada", located("a", "", "CA", "QC").region("en"))
        assertEquals("BY, Germany", located("a", "", "DE", "BY").region("en"))
        assertEquals("Canada", located("a", "", "CA", "").region("en"))
        assertEquals("QC", located("a", "", "", "QC").region("en"))
        assertEquals("", located("a", "", "", "").region("en"))
    }

    @Test fun `P-18 ten meets are cards with their images, eleven are compact rows that fetch none`() {
        val server = ServerAddress.parseOrNull("https://c.example")!!
        fun list(n: Int) = PickerState(
            meets = List(n) { MeetSummary("m$it", "Meet $it", "", "", "", "", offline = false, hasPickerImage = true) },
        )
        val ten = list(10)
        assertFalse(ten.compact)
        assertTrue(ten.reserveImage)
        assertEquals("https://c.example/picker_image/m0", ten.imageUrl(server, ten.meets[0]))
        assertEquals(10, ten.meets.mapNotNull { ten.imageUrl(server, it) }.size)

        val eleven = list(11)
        assertTrue(eleven.compact)
        assertFalse(eleven.reserveImage)
        assertEquals(emptyList(), eleven.meets.mapNotNull { eleven.imageUrl(server, it) })
        // counted on the whole list: a query that leaves three does not bring images back
        val searched = eleven.copy(searching = true, query = "meet 1")
        assertTrue(searched.compact)
        assertNull(searched.imageUrl(server, searched.shownMeets.first()))
        // a meet without an image never asks, whatever the count
        assertNull(list(1).let { it.imageUrl(server, it.meets[0].copy(hasPickerImage = false)) })
    }
}
