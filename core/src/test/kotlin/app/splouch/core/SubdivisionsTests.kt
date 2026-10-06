package app.splouch.core

import app.splouch.core.session.MeetSearch
import app.splouch.core.session.Subdivisions
import app.splouch.core.wire.MeetSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P-01, P-21: states and provinces named in full from the bundled table. */
class SubdivisionsTests {
    @Test fun `known spellings are named in full`() {
        assertEquals("Québec", Subdivisions.name("CA", "QC"))
        assertEquals("Québec", Subdivisions.name("CA", "quebec"))
        assertEquals("Québec", Subdivisions.name("CA", "PQ"))
        assertEquals("Ontario", Subdivisions.name("ca", "on"))
        assertEquals("New York", Subdivisions.name("US", "NY"))
        assertEquals("Ciudad de México", Subdivisions.name("MX", "CDMX"))
        assertEquals("Nuevo León", Subdivisions.name("MX", "NL"))
    }

    /** The code means nothing without its country: `CA` is California only in the US. */
    @Test fun `a province is looked up in its own country`() {
        assertNull(Subdivisions.lookup("US", "QC"))
        assertEquals("QC", Subdivisions.name("", "QC"))
        assertEquals("California", Subdivisions.name("US", "CA"))
    }

    @Test fun `an unknown province is shown as sent`() {
        assertEquals("Bayern", Subdivisions.name("DE", "Bayern"))
        assertEquals("", Subdivisions.name("CA", ""))
    }

    @Test fun `a meet is found by its province's full name`() {
        val m = MeetSummary("m", "Invitational", "", "", "", "", offline = false, hasPickerImage = false, "CA", "QC")
        assertEquals(1, MeetSearch.filter(listOf(m), "québec").size)
    }
}
