package app.splouch.core

import app.splouch.core.session.InMemoryVidStore
import app.splouch.core.session.VidEntry
import app.splouch.core.session.VidStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** app.md C-10: counting the spectator can refuse, and an id that does not live forever. */
class VidStoreTests {
    private val origin = "https://c.example"
    private val day = 24 * 3600 * 1000L
    private var now = 1_000_000_000_000L
    private val vids = InMemoryVidStore { now }

    @Test fun `on by default, created on first use and dated then`() {
        assertTrue(vids.counting(origin))
        assertNull(vids.entries[origin])
        val v = vids.vid(origin)!!
        assertEquals(VidEntry(v, now, counting = true), vids.entries[origin])
        assertEquals(v, vids.vid(origin))
    }

    @Test fun `off deletes the vid and creates none until turned back on`() {
        vids.vid(origin)
        vids.setCounting(origin, false)
        assertFalse(vids.counting(origin))
        assertEquals(VidEntry(counting = false), vids.entries[origin])
        assertNull(vids.vid(origin))
        assertNull(vids.vid(origin))
        assertEquals(VidEntry(counting = false), vids.entries[origin])
    }

    @Test fun `back on makes a new vid, never the old one`() {
        val old = vids.vid(origin)!!
        vids.setCounting(origin, false)
        vids.setCounting(origin, true)
        // Turning it on creates nothing by itself; the next join does.
        assertNull(vids.entries[origin]!!.id)
        val fresh = vids.vid(origin)!!
        assertNotEquals(old, fresh)
    }

    @Test fun `a vid older than 13 months is replaced, a younger one kept`() {
        val v = vids.vid(origin)!!
        now += VidStore.MAX_AGE_MS
        assertEquals(v, vids.vid(origin))
        now += day
        val replaced = vids.vid(origin)!!
        assertNotEquals(v, replaced)
        assertEquals(now, vids.entries[origin]!!.createdAt)
    }

    @Test fun `an undated vid from before dates were kept is dated now, not replaced`() {
        vids.entries[origin] = VidEntry("old-id", createdAt = null)
        assertEquals("old-id", vids.vid(origin))
        assertEquals(now, vids.entries[origin]!!.createdAt)
        // And it ages from today, not from whenever it was really made.
        now += VidStore.MAX_AGE_MS
        assertEquals("old-id", vids.vid(origin))
    }

    @Test fun `each server keeps its own setting and its own vid`() {
        val other = "http://pi.local:5000"
        val v = vids.vid(other)!!
        vids.setCounting(origin, false)
        assertTrue(vids.counting(other))
        assertEquals(v, vids.vid(other))
        assertNull(vids.vid(origin))
    }
}
