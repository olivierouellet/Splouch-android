package app.splouch.core.board

import kotlin.math.max
import kotlin.math.min

/**
 * The board's sizing rules (`L-15`, `L-16`, `L-17`, `R-08`), as numbers in `dp` and nothing
 * else. The Compose side measures, hands the measurements in here and converts what comes
 * back to `sp` exactly once — so the rules can be tested on a JDK, and every size on the board
 * stays pinned to the height and width it was measured from whatever the font-size setting is.
 *
 * The same rules as the web board (`scoreboard_base.html`): lane share drives the type, a
 * 56 ceiling on the full table, proportional columns, and shrink-to-fit with floors.
 */
object BoardSizing {
    /**
     * Full table: the row type is this share of each lane's height. A full-table row has no
     * vertical padding, so this alone holds the type down; 0.55 leaves room for a name with a
     * relay line under it (0.85 + 0.5 of the row font).
     */
    const val TYPE_SHARE = 0.55f

    /** Full table: the row font's floor and ceiling. 56 keeps four lanes on a tablet off poster size. */
    const val ROW_FONT_MIN = 10f
    const val ROW_FONT_MAX = 56f

    /**
     * Full table: below this row font the column titles cost more height than their words are
     * worth, so the table drops them and gives the band back to the lanes.
     */
    const val HEADER_FLOOR = 14f

    /** Compact board: the base type is this share of each lane's height, clamped (`L-15`). */
    const val COMPACT_SHARE = 0.26f
    const val COMPACT_FONT_MIN = 13f
    const val COMPACT_FONT_MAX = 24f

    /**
     * A shrinking cell stops at half its size and never below 10 — the Qt board's floor. Past
     * that the ellipsis says there is more better than type nobody reads from a seat.
     */
    const val FIT_FLOOR = 0.5f
    const val FIT_FLOOR_DP = 10f

    /**
     * The widest a time can be: a 1500 is `18:05.33`. The time column is sized for this once,
     * never for the value in it — the running clock rewrites the cell ten times a second, and
     * a per-value fit would jump the size the moment `59.9` became `1:00.0`.
     */
    const val TIME_TEMPLATE = "88:88.88"

    /** The delta column's floor template; a longer delta on the board widens it. */
    const val DELTA_TEMPLATE = "+88.88"

    /**
     * Full table: the row font for [lanes] sharing [availableDp] of height, no bigger than
     * [widthCapDp] (see [widthCap]).
     */
    fun rowFont(availableDp: Float, lanes: Int, widthCapDp: Float = ROW_FONT_MAX): Float =
        (availableDp * TYPE_SHARE / lanes.coerceAtLeast(1))
            .coerceIn(ROW_FONT_MIN, max(ROW_FONT_MIN, min(ROW_FONT_MAX, widthCapDp)))

    /**
     * Full table: the biggest row font at which a column still holds its template at the
     * [FIT_FLOOR] — past it, the column would clip whatever its fit said. [roomDp] is the
     * column's width less padding, [templatePerDp] the template's width per dp of type, and
     * [share] the column's size as a fraction of the row font.
     *
     * Only an upright tablet gets near it: tall rows reach the 56 ceiling while the time
     * column is 17% of 800dp, and `88:88.88` at 0.85 × 56 cannot fit there at any fit ≥ 0.5.
     */
    fun widthCap(roomDp: Float, templatePerDp: Float, share: Float): Float =
        if (roomDp <= 0f || templatePerDp <= 0f) ROW_FONT_MAX else roomDp * 0.97f / (templatePerDp * share * FIT_FLOOR)

    /** The place column's template: `#` and as many 8s as the highest lane number has digits. */
    fun placeTemplate(lanes: Int): String = "#" + "8".repeat(lanes.coerceAtLeast(1).toString().length)

    /**
     * Full table: whether the column titles are drawn. The rows are sized with the titles'
     * [headerBandDp] withheld; if that comes out under [HEADER_FLOOR] the titles go.
     *
     * Reads the *cached* band, never the height its own answer changes, so it cannot
     * oscillate; and dropping the titles only ever makes the type bigger. The 56 ceiling does
     * not touch this: it is reached only well above the floor, where the titles always stay.
     * A band of 0 is "not measured yet", and the titles are drawn so it can be.
     */
    fun showsHeader(anyHeader: Boolean, heightDp: Float, headerBandDp: Float, lanes: Int): Boolean =
        anyHeader && (headerBandDp <= 0f || rowFont(heightDp - headerBandDp, lanes) >= HEADER_FLOOR)

    /** Compact board: the base type for a lane [shareDp] tall, before `L-24`'s scale. */
    fun compactFont(shareDp: Float): Float = (shareDp * COMPACT_SHARE).coerceIn(COMPACT_FONT_MIN, COMPACT_FONT_MAX)

    /**
     * How far a cell set at [maxDp] may shrink: to [minDp] — half of it unless the cell has a
     * ratio of its own — but never under 10, unless the cell started under 10.
     */
    fun shrinkFloor(maxDp: Float, minDp: Float = maxDp * FIT_FLOOR): Float = max(minDp, min(maxDp, FIT_FLOOR_DP))

    /**
     * One factor for a whole column: 1 when [need] fits [room], else the ratio with a 3%
     * margin, floored at [FIT_FLOOR]. Units are the caller's, as long as they are the same.
     */
    fun columnFit(room: Float, need: Float): Float =
        if (room <= 0f || need <= room) 1f else max(FIT_FLOOR, room / need * 0.97f)

    /** What the delta column must hold: the template, and any delta or lap on the board. */
    fun deltaTexts(onBoard: Iterable<String>): List<String> =
        listOf(DELTA_TEMPLATE) + onBoard.filter { it.isNotEmpty() }
}

/**
 * The full table's columns as fractions of the row width (`L-16`), the ones the web board
 * uses: lane 6 · club 14 · time 17 · delta 12 · place 6, the name the rest. A hidden column's
 * share goes to the name; a hidden name's goes to the club, else to an empty slot where the
 * name would be, so the columns after it stay where their titles are.
 *
 * Header and rows both read this one value, which is what keeps every title over its column.
 * A hidden column is `0f`.
 */
data class BoardColumns(
    val lane: Float,
    val name: Float,
    val club: Float,
    val time: Float,
    val delta: Float,
    val place: Float,
    /** An empty slot standing in for a hidden name with no club to take its share. */
    val spacer: Float,
) {
    companion object {
        const val LANE = 0.06f
        const val CLUB = 0.14f
        const val TIME = 0.17f
        const val DELTA = 0.12f
        const val PLACE = 0.06f

        fun of(showName: Boolean, showClub: Boolean, showDelta: Boolean, showPosition: Boolean): BoardColumns {
            val club = if (showClub) CLUB else 0f
            val delta = if (showDelta) DELTA else 0f
            val place = if (showPosition) PLACE else 0f
            val rest = 1f - LANE - club - TIME - delta - place
            return when {
                showName -> BoardColumns(LANE, rest, club, TIME, delta, place, 0f)
                showClub -> BoardColumns(LANE, 0f, club + rest, TIME, delta, place, 0f)
                else -> BoardColumns(LANE, 0f, 0f, TIME, delta, place, rest)
            }
        }
    }
}
