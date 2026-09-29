package app.splouch.core

import app.splouch.core.session.MeetSearch
import app.splouch.core.session.PickerState
import app.splouch.core.wire.MeetSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
