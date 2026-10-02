package app.splouch.core

import app.splouch.core.board.BoardColumns
import app.splouch.core.board.BoardSizing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardSizingTests {
    // ── L-16: the full table's row font ──────────────────────────────────────

    @Test fun `row font is 55 percent of the lane share`() {
        assertEquals(330f * 0.55f / 6, BoardSizing.rowFont(330f, 6), 0.001f)
    }

    @Test fun `row font caps at 56, not 32`() {
        // A tablet on its side, four lanes: 700 × 0.55 / 4 = 96, and the old cap set it at 32.
        assertEquals(56f, BoardSizing.rowFont(700f, 4))
        assertEquals(44f, BoardSizing.rowFont(800f, 10), 0.001f)
    }

    @Test fun `row font floors at 10`() {
        assertEquals(10f, BoardSizing.rowFont(100f, 10))
        assertEquals(10f, BoardSizing.rowFont(-20f, 6))
    }

    @Test fun `zero lanes are one lane`() {
        assertEquals(BoardSizing.rowFont(200f, 1), BoardSizing.rowFont(200f, 0))
    }

    @Test fun `width caps the row font so the time column holds its template at the floor`() {
        // Upright tablet: a 110dp time column, `88:88.88` about 5.2dp wide per dp of type,
        // set at 0.85 of the row: 110 × 0.97 / (5.2 × 0.85 × 0.5) = 48.3 — under the 56 the
        // height alone would give.
        val cap = BoardSizing.widthCap(110f, 5.2f, 0.85f)
        assertEquals(48.28f, cap, 0.01f)
        assertEquals(cap, BoardSizing.rowFont(1100f, 10, cap), 0.001f)
        // At that size the column's fit lands on the floor exactly, and never clips.
        assertEquals(0.5f, BoardSizing.columnFit(110f, 5.2f * 0.85f * cap), 0.0001f)
    }

    @Test fun `a wide column leaves the height rule alone`() {
        val cap = BoardSizing.widthCap(400f, 5.2f, 0.85f)
        assertEquals(56f, BoardSizing.rowFont(700f, 4, cap))
        assertEquals(330f * 0.55f / 6, BoardSizing.rowFont(330f, 6, cap), 0.001f)
    }

    @Test fun `the width cap never pushes the font under its floor`() {
        assertEquals(10f, BoardSizing.rowFont(700f, 4, widthCapDp = 3f))
    }

    @Test fun `an unmeasured column does not cap`() {
        assertEquals(56f, BoardSizing.widthCap(0f, 5f, 0.85f))
        assertEquals(56f, BoardSizing.widthCap(100f, 0f, 0.85f))
    }

    @Test fun `place template has the lane count's digits`() {
        assertEquals("#8", BoardSizing.placeTemplate(6))
        assertEquals("#88", BoardSizing.placeTemplate(10))
    }

    // ── L-16: the column titles ──────────────────────────────────────────────

    @Test fun `titles are drawn until they are measured`() {
        assertTrue(BoardSizing.showsHeader(true, 100f, 0f, 10))
    }

    @Test fun `titles go when the rows under them would drop below 14`() {
        // 300 − 22 = 278 × 0.55 / 12 = 12.7 → drop; 600 − 22 → 27.9 → keep.
        assertFalse(BoardSizing.showsHeader(true, 300f, 22f, 12))
        assertTrue(BoardSizing.showsHeader(true, 600f, 22f, 12))
    }

    @Test fun `no title to draw is no title row`() {
        assertFalse(BoardSizing.showsHeader(false, 900f, 22f, 4))
    }

    @Test fun `the 56 cap never takes the titles away`() {
        // Every height that reaches the cap is far above the floor: bigger rows do not fight
        // the title row for height.
        for (lanes in 1..12) {
            for (h in 200..1600 step 10) {
                val capped = BoardSizing.rowFont(h - 22f, lanes) >= BoardSizing.ROW_FONT_MAX
                if (capped) assertTrue(BoardSizing.showsHeader(true, h.toFloat(), 22f, lanes))
            }
        }
    }

    @Test fun `the decision does not depend on its own answer`() {
        // Same inputs, same answer: it reads the cached band, not the height it changes.
        val a = BoardSizing.showsHeader(true, 320f, 24f, 10)
        val b = BoardSizing.showsHeader(true, 320f, 24f, 10)
        assertEquals(a, b)
    }

    // ── L-15: the compact board's base type is unchanged ─────────────────────

    @Test fun `compact font is 26 percent of the share, 13 to 24`() {
        assertEquals(13f, BoardSizing.compactFont(30f))
        assertEquals(60f * 0.26f, BoardSizing.compactFont(60f), 0.001f)
        assertEquals(24f, BoardSizing.compactFont(200f))
    }

    // ── L-17 / R-08: shrink floors ───────────────────────────────────────────

    @Test fun `a cell shrinks to half, never under 10`() {
        assertEquals(20f, BoardSizing.shrinkFloor(40f))
        assertEquals(10f, BoardSizing.shrinkFloor(16f))
        // Started under 10: it may not shrink at all rather than grow.
        assertEquals(8f, BoardSizing.shrinkFloor(8f))
    }

    @Test fun `a name keeps its own ratio, with the 10 floor under it`() {
        assertEquals(14.4f, BoardSizing.shrinkFloor(24f, 24f * 0.6f), 0.001f)
        assertEquals(10f, BoardSizing.shrinkFloor(13f, 13f * 0.6f))
    }

    // ── L-17: time and delta, one size per column ────────────────────────────

    @Test fun `a column that fits is left alone`() {
        assertEquals(1f, BoardSizing.columnFit(120f, 100f))
        assertEquals(1f, BoardSizing.columnFit(100f, 100f))
    }

    @Test fun `a column that does not fit shrinks by the ratio, with a margin`() {
        assertEquals(100f / 125f * 0.97f, BoardSizing.columnFit(100f, 125f), 0.0001f)
    }

    @Test fun `a column never shrinks under half`() {
        assertEquals(0.5f, BoardSizing.columnFit(100f, 1000f))
    }

    @Test fun `a column not laid out yet is not shrunk`() {
        assertEquals(1f, BoardSizing.columnFit(0f, 100f))
    }

    @Test fun `the time template covers a 1500`() {
        assertEquals("18:05.33".length, BoardSizing.TIME_TEMPLATE.length)
        assertTrue(BoardSizing.TIME_TEMPLATE.all { it == '8' || it == ':' || it == '.' })
    }

    @Test fun `delta texts are the template plus what is on the board`() {
        assertEquals(listOf("+88.88"), BoardSizing.deltaTexts(listOf("", "")))
        assertEquals(listOf("+88.88", "+1:02.50", "4"), BoardSizing.deltaTexts(listOf("+1:02.50", "", "4")))
    }

    // ── L-16: proportional columns ───────────────────────────────────────────

    @Test fun `all columns shown`() {
        val c = BoardColumns.of(showName = true, showClub = true, showDelta = true, showPosition = true)
        assertEquals(0.06f, c.lane)
        assertEquals(0.14f, c.club)
        assertEquals(0.17f, c.time)
        assertEquals(0.12f, c.delta)
        assertEquals(0.06f, c.place)
        assertEquals(0.45f, c.name, 0.0001f)
        assertEquals(0f, c.spacer)
    }

    @Test fun `every show combination fills the row exactly, hidden columns at zero`() {
        for (mask in 0 until 16) {
            val name = mask and 1 != 0
            val club = mask and 2 != 0
            val delta = mask and 4 != 0
            val place = mask and 8 != 0
            val c = BoardColumns.of(name, club, delta, place)
            val sum = c.lane + c.name + c.club + c.time + c.delta + c.place + c.spacer
            assertEquals(1f, sum, 0.0001f, "mask $mask")
            // The fixed columns keep their share whatever else is hidden: no title drifts.
            assertEquals(BoardColumns.LANE, c.lane)
            assertEquals(BoardColumns.TIME, c.time)
            assertEquals(if (delta) BoardColumns.DELTA else 0f, c.delta, "mask $mask")
            assertEquals(if (place) BoardColumns.PLACE else 0f, c.place, "mask $mask")
            if (!name) assertEquals(0f, c.name, "mask $mask")
            if (!club) assertEquals(0f, c.club, "mask $mask")
            // A hidden column's share goes to the name; with no name, to the club; with
            // neither, to a slot where the name would be.
            when {
                name -> {
                    assertEquals(0f, c.spacer)
                    assertEquals(if (club) BoardColumns.CLUB else 0f, c.club, "mask $mask")
                }
                club -> assertEquals(0f, c.spacer)
                else -> assertTrue(c.spacer > 0f, "mask $mask")
            }
        }
    }

    @Test fun `a hidden club, delta and place all go to the name`() {
        val c = BoardColumns.of(showName = true, showClub = false, showDelta = false, showPosition = false)
        assertEquals(1f - 0.06f - 0.17f, c.name, 0.0001f)
    }
}
