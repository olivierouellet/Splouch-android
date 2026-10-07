package app.splouch.core

import app.splouch.core.session.MeetDay
import app.splouch.core.session.MeetFilter
import app.splouch.core.session.place
import app.splouch.core.wire.MeetSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** P-01: the cards under their day, and the one line of place each card carries. */
class MeetDaysTests {
    private fun meet(id: String = "x", date: String = "", country: String = "", province: String = "") =
        MeetSummary(id, id, "Pool", "Swimming", "", date, offline = false, hasPickerImage = false, country, province)

    @Test fun `meets go under their day, an undated one last, in the server's order within a day`() {
        val days = MeetDay.group(
            listOf(meet("a"), meet("b", "2026-10-07"), meet("c", "2026-10-06"), meet("d", "2026-10-07")),
        )
        assertEquals(listOf("2026-10-06", "2026-10-07", ""), days.map { it.date })
        assertEquals(listOf("b", "d"), days[1].meets.map { it.id })
    }

    @Test fun `the card shows the city and the codes, never the day or the sport`() {
        assertEquals(listOf("Pool", "QC", "CA"), meet(country = "CA", province = "Québec").place())
        assertEquals(listOf("Pool", "Bayern", "DE"), meet(country = "de", province = "Bayern").place())
        assertEquals(listOf("Pool"), meet().place())
    }

    @Test fun `what the filter pins to one is not repeated`() {
        val qc = meet(country = "CA", province = "QC")
        assertEquals(listOf("Pool", "QC"), qc.place(MeetFilter(countries = setOf("CA"))))
        assertEquals(listOf("Pool", "QC", "CA"), qc.place(MeetFilter(countries = setOf("CA", "US"))))
        val quebec = MeetFilter.Province("CA", "Québec")
        val qcCode = MeetFilter.Province("CA", "QC")
        val on = MeetFilter.Province("CA", "ON")
        // Two spellings of one province are one choice.
        assertEquals(listOf("Pool"), qc.place(MeetFilter(provinces = setOf(quebec, qcCode))))
        assertEquals(listOf("Pool", "QC"), qc.place(MeetFilter(provinces = setOf(quebec, on))))
        // Every value is an alternative: a club's meets, or another country's, may show too.
        assertEquals(listOf("Pool", "QC", "CA"), qc.place(MeetFilter(setOf("CA"), clubs = setOf("CAMO"))))
        assertEquals(listOf("Pool", "QC"), qc.place(MeetFilter(setOf("CA"), setOf(qcCode))))
        assertEquals(listOf("Pool", "QC", "CA"), qc.place(MeetFilter(setOf("US"), setOf(qcCode))))
    }
}
