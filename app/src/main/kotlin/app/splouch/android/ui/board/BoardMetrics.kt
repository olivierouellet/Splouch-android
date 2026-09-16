package app.splouch.android.ui.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the board has measured about itself on this device, and the one decision taken from
 * those measurements (`L-15`).
 *
 * **Nothing here is a constant, and that is the point.** The iOS twin first shipped four of
 * them — a lane's natural height, the chrome the bars take, the header band, a reserve for
 * the navigation bar — all measured on one phone. They were wrong on everything else and
 * took many build-and-look cycles to tune. So a lane measures itself, the header measures
 * itself, and the system bars are `Scaffold`'s business and already subtracted before the
 * board sees a single `dp`.
 *
 * Held by `MeetShell`, which is the only place that knows both what the lanes want and what
 * the app bar is currently carrying.
 */
@Stable
class BoardMetrics {
    /**
     * A lane's natural height at full size, measured from a row that is laid out and never
     * drawn. `0.dp` until the first layout has run, which is not a problem to defend
     * against: a row then wants exactly its share, so the first frame draws at full size and
     * settles a frame later. That beats a number that is right on one phone and wrong on the
     * rest.
     */
    var laneIdeal by mutableStateOf(0.dp)

    /** The same lane carrying a relay name (`L-06`), which is a whole extra line. */
    var laneIdealWithAlt by mutableStateOf(0.dp)

    /**
     * What the board's own header row costs, measured while it is being drawn and kept after
     * it moves into the app bar — which is exactly the value needed to decide whether to
     * move it back.
     */
    var headerBand by mutableStateOf(0.dp)

    /** Whether the lanes have asked the app bar to take the header row. */
    var needsBar by mutableStateOf(false)

    /**
     * Whether the lanes need the app bar to take the `EVENT`/`HEAT` row.
     *
     * **Measured against the height they would have _with_ the row in the bar**, which is
     * the same number whichever way the answer comes out — so moving the row cannot hand
     * back the space that caused the move and flip it straight back. Without that
     * normalisation this oscillates at frame rate: move the header up, gain its height,
     * decide it was not needed, move it back, lose the height, decide it was.
     *
     * [available] is the height the lanes have *now*, so it is short by [headerBand] exactly
     * when the header is not yet in the bar; both branches therefore come out at the full
     * height of the page. The result depends only on the lane count, the two measurements
     * and the screen — never on its own previous answer.
     */
    fun wantsBar(available: Dp, headerInBar: Boolean, lanes: Int): Boolean {
        if (laneIdeal <= 0.dp || headerBand <= 0.dp) return needsBar
        val withTheBar = if (headerInBar) available else available + headerBand
        return laneIdeal * lanes.coerceAtLeast(1) > withTheBar
    }
}

/**
 * How far the portrait type may be shrunk to keep a heat on one screen. Past this the table
 * overflows and scrolls, which is the honest answer: twelve lanes of relay at 8dp would fit
 * and be unreadable.
 */
internal const val PortraitTypeFloor = 0.72f
