package app.splouch.android.ui.board

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.splouch.android.ui.common.reduceMotion
import app.splouch.android.ui.theme.AutoSizeText
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.android.ui.theme.LocalBoardFonts
import app.splouch.core.board.BoardColumns
import app.splouch.core.board.BoardSizing
import app.splouch.core.board.DeltaFormat
import app.splouch.core.board.LapCount
import app.splouch.core.board.LapSettings
import app.splouch.core.board.ScoreboardState.TimeStyle
import app.splouch.core.wire.MeetSettings
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/** One row of the lane grid, for either tab. */
data class GridRow(
    val lane: String,
    val pulsing: Boolean = false,
    val name: String = "",
    val alt: String = "",
    val club: String = "",
    val time: String = "",
    val timeStyle: TimeStyle = TimeStyle.NORMAL,
    val lockEdge: Int = 0,
    val deltaSeconds: Double? = null,
    val deltaBetter: Boolean? = null,
    val place: String = "",
    /**
     * `L-23`: the delta cell's other tenant, or null when the cell belongs to the delta or to
     * nothing. Decided in the core off merged state and handed down here — the Results tab is
     * all finishes and never has one.
     */
    val lap: LapCount? = null,
    /** L-25: nothing in the lane. Drawn as still water only when the heat has names. */
    val vacant: Boolean = false,
)

/**
 * L-25: a lane only reads as empty against lanes that are not. A console that sends no names
 * at all leaves every lane bare, and a pool of still water there would be a claim the board
 * cannot make.
 */
private fun stillLanes(rows: List<GridRow>): List<Boolean> {
    val named = rows.any { it.name.isNotEmpty() }
    return rows.map { named && it.vacant }
}

/** The six-column board shared by the Scoreboard and Results tabs (app.md L-04..L-09, L-15..L-17, R-04). */
@Composable
fun BoardGrid(
    rows: List<GridRow>,
    settings: MeetSettings,
    labels: Map<String, String>,
    /** `L-15` / `L-16`: the window's width class, not its orientation. */
    wide: Boolean,
    metrics: BoardMetrics,
    /** Whether the app bar is already carrying the `EVENT`/`HEAT` row (`L-15`, `L-16`). */
    headerInBar: Boolean,
    modifier: Modifier = Modifier,
    /**
     * `L-23`, and the Scoreboard tab's alone: the lap shares the delta *cell*, but the column
     * header it takes down is the whole column's. The Results tab is every lane's finish, where
     * the cell only ever holds a delta, so `R-04`'s header keeps its word.
     */
    laps: LapSettings = LapSettings.OFF,
    /** `X-01`: the word a lap count is spoken with — `[mobile] spoken_laps`, the server's (`T-05`). */
    lapsWord: String,
) {
    BoardType {
        if (wide) {
            LandscapeGrid(rows, settings, labels, laps, lapsWord, modifier)
        } else {
            PortraitGrid(rows, settings, labels, metrics, headerInBar, lapsWord, modifier)
        }
    }
}

/**
 * The board's line box, pinned the way its type is.
 *
 * Material's `Text` inherits `LocalTextStyle`, whose `lineHeight` is a fixed `24.sp` off the
 * type scale — and `sp` keeps following the device's font-size setting however the
 * `fontSize` beside it was worked out. So a board that had carefully sized itself from the
 * height it has still got a **48dp line box for 15dp type** at 2×, and every row overflowed
 * the height it had been measured into: the second scaling channel, hidden behind the first
 * (`parity.md` §8).
 *
 * `Unspecified` hands the line box back to the font, which makes it proportional to the
 * `fontSize` — the one number the board controls. This covers the Material `Text`s; the
 * `BasicText` behind `AutoSizeText` never inherited a `lineHeight` to begin with.
 */
@Composable
internal fun BoardType(content: @Composable () -> Unit) {
    val style = LocalTextStyle.current
    CompositionLocalProvider(
        LocalTextStyle provides remember(style) { style.copy(lineHeight = TextUnit.Unspecified) },
        content = content,
    )
}

/**
 * A lane is one thing, not six unrelated fragments — so the row is a single accessibility
 * element reading the whole lane, composed from the server's own column words (`T-04`) so
 * it is spoken in the meet's language rather than the app's. An empty lane says only its
 * number (`L-25`), and an unfilled rank nothing.
 */
private fun spoken(r: GridRow, settings: MeetSettings, labels: Map<String, String>, lapsWord: String): String {
    fun word(key: String) = labels[key].orEmpty()
    val parts = mutableListOf<String>()
    // An unfilled rank in place order has no lane, and says nothing (`R-06`).
    if (r.lane.isNotBlank()) parts += "${word("lane")} ${r.lane}".trim()
    // L-25: nobody in the lane — its number alone. Not the Results row's `—`, which is
    // drawn under the water and was never a time.
    if (r.vacant) return parts.joinToString(", ")
    if (settings.showName && r.name.isNotBlank()) parts += r.name
    if (settings.showName && r.alt.isNotBlank()) parts += r.alt
    if (settings.showClub && r.club.isNotBlank()) parts += "${word("club")} ${r.club}".trim()
    if (r.time.isNotBlank()) parts += "${word("time")} ${r.time}".trim()
    if (settings.showDelta &&
        r.deltaSeconds != null
    ) {
        parts += "${word("delta")} ${DeltaFormat.text(r.deltaSeconds)}".trim()
    }
    // No column names a lap, so its word is `[mobile] spoken_laps` (`X-01`), the one the web
    // board speaks too — not a bare integer heard as a second time.
    if (settings.showDelta && r.lap != null) parts += "$lapsWord ${r.lap.text}".trim()
    if (settings.showPosition && r.place.isNotBlank()) parts += "${word("place")} ${r.place}".trim()
    return parts.joinToString(", ")
}

// ── portrait: the two-line compact row (L-15) ────────────────────────────────

@Composable
private fun PortraitGrid(
    rows: List<GridRow>,
    settings: MeetSettings,
    labels: Map<String, String>,
    metrics: BoardMetrics,
    headerInBar: Boolean,
    lapsWord: String,
    modifier: Modifier,
) {
    val colors = LocalBoardColors.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val count = rows.size.coerceAtLeast(1)
        // The lanes share the whole height, equally, and there is no floor under a row: the
        // share each row gets *is* the floor, and the type shrinks to meet it. The 44dp floor
        // that used to be here was what stopped twelve lanes fitting — twelve of them wanted
        // 528dp against the ~510 a phone has, and the miss was the floor, not the type.
        // The last lane is not special-cased: `Scaffold` has already taken the system bars
        // off this height, so it clears the navigation bar on its own.
        val share = maxHeight / count
        // The type follows the height the rows actually got, so a six-lane meet is read
        // across the pool rather than set at the size a sixteen-lane one needs. Through
        // `toSp()` — see `parity.md` §8.
        val base = BoardSizing.compactFont(share.value).dp

        // Degrade in the order a spectator loses least by, and each only on need. The relay
        // name goes before any type shrinks: it is the one line on the row that is not a
        // swimmer, a time or a place, and a team name set at 9dp to keep it helps nobody.
        val anyAlt = settings.showName && rows.any { it.alt.isNotEmpty() }
        val wantPlain = if (metrics.laneIdeal > 0.dp) metrics.laneIdeal else share
        val wantAlt = if (metrics.laneIdealWithAlt > 0.dp) metrics.laneIdealWithAlt else wantPlain
        val showsAlt = anyAlt && share >= wantAlt
        val want = if (showsAlt) wantAlt else wantPlain
        // Then the type scales to the share, floored — past which the board scrolls rather
        // than fit twelve lanes of relay at a size nobody can read.
        val scale = (if (want > 0.dp) share / want else 1f).coerceIn(PORTRAIT_TYPE_FLOOR, 1f)
        val rowHeight = maxOf(share, want * scale)

        // L-17: the time and the delta on line two take one size each for the whole board,
        // fitted to the widest thing they can hold — `88:88.88`, and `+88.88` or the longest
        // delta or lap on the board — and never to the ticking value. One factor for the pair,
        // since they share the line's width. The rulers are fitted at full size, the rows at
        // the size they are drawn at.
        val fonts = LocalBoardFonts.current
        val deltaTexts =
            if (settings.showDelta) BoardSizing.deltaTexts(rows.map(::deltaCellText)) else emptyList()

        @Composable
        fun lineFit(s: Float): Float {
            val room = with(density) {
                (
                    maxWidth - LaneW * s - RowEndPad * s -
                        (if (settings.showPosition) PlaceW * s else 0.dp) -
                        (if (settings.showDelta) CellGutter * s else 0.dp)
                    ).toPx()
            }
            return rememberLineFit(fonts.timing, base * s, room, deltaTexts)
        }
        val rulerFit = lineFit(1f)
        val rowFit = lineFit(scale)
        val clubMax = (maxWidth - LaneW - RowEndPad) * CLUB_SHARE

        // And above both of those, the app bar taking the EVENT / HEAT row. Reported rather
        // than decided here: `MeetShell` is the only place that knows what the bar is
        // carrying. `SideEffect` because this is a write for the *next* composition.
        val wantsBar = metrics.wantsBar(maxHeight, headerInBar, settings.numLanes)
        SideEffect { metrics.needsBar = wantsBar }

        Box(Modifier.fillMaxSize()) {
            // The rulers: one lane at full size, and the same lane carrying a relay name,
            // laid out with the table's width and never drawn. They can measure without
            // moving what they measure because this `Box` is already pinned to the height
            // `BoxWithConstraints` handed it — the feedback an `onGloballyPositioned` among
            // the real rows would have made. `alpha(0f)` rather than skipping them, because
            // they have to be laid out to have a height at all, and out of the accessibility
            // tree because they are a ruler and not a lane.
            if (rows.isNotEmpty()) {
                Column(Modifier.alpha(0f).clearAndSetSemantics { }) {
                    PortraitRow(
                        rows.first(),
                        settings,
                        base,
                        scale = 1f,
                        showsAlt = false,
                        lineFit = rulerFit,
                        clubMax = clubMax,
                        modifier = Modifier.onSizeChanged { with(density) { metrics.laneIdeal = it.height.toDp() } },
                    )
                    // Real content, not a stand-in: the height of a relay row is the height
                    // of the words actually in it.
                    rows.firstOrNull { it.alt.isNotEmpty() }?.let { relay ->
                        PortraitRow(
                            relay,
                            settings,
                            base,
                            scale = 1f,
                            showsAlt = true,
                            lineFit = rulerFit,
                            clubMax = clubMax,
                            modifier = Modifier.onSizeChanged {
                                with(density) {
                                    metrics.laneIdealWithAlt =
                                        it.height.toDp()
                                }
                            },
                        )
                    }
                }
            }
            val still = stillLanes(rows)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, r ->
                    val description = spoken(r, settings, labels, lapsWord)
                    PortraitRow(
                        r,
                        settings,
                        base,
                        scale,
                        showsAlt,
                        rowFit,
                        clubMax * scale,
                        still = still[i],
                        modifier = Modifier.height(rowHeight)
                            .background(if (i % 2 == 0) colors.rowOdd else colors.rowEven)
                            .clearAndSetSemantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

/**
 * L-15's two-line compact row: lane number spanning the left, name on line one with the club
 * right-aligned, time and delta and place on line two.
 *
 * [scale] is 1 when the heat fits at the sizes below and less when the lanes have to share
 * the screen more tightly. **Everything** scales together — the type, the spacing, the lane
 * number's column and the vertical padding — so the hierarchy holds at any lane count.
 * [showsAlt] is false once the lanes are too tight to spend a line on the relay's name.
 * [lineFit] is the one factor the time and delta take on every row (`L-17`), and [clubMax]
 * the most of line one the club may take before it shrinks.
 */
@Composable
private fun PortraitRow(
    r: GridRow,
    settings: MeetSettings,
    base: Dp,
    scale: Float,
    showsAlt: Boolean,
    lineFit: Float,
    clubMax: Dp,
    modifier: Modifier = Modifier,
    /** L-25: nobody in this lane, so the row is still water rather than cells. */
    still: Boolean = false,
) {
    val colors = LocalBoardColors.current
    val density = LocalDensity.current
    val size = base * scale
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LaneNumber(r.lane, r.pulsing, size, Modifier.width(LaneW * scale))
        if (still) {
            StillWater(size * 0.15f, Modifier.weight(1f).fillMaxHeight().padding(end = RowEndPad * scale))
            return@Row
        }
        Column(Modifier.weight(1f).padding(end = RowEndPad * scale)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // The floor comes off the same pinned size as the ceiling — see `AutoSizeText`.
                if (settings.showName) {
                    AutoSizeText(
                        r.name,
                        Modifier.weight(1f),
                        maxSize = size.asSp(),
                        minSize = shrinkFloor(size, size * 0.6f).asSp(),
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
                // Club, delta and place read at the name's size rather than a quarter under
                // it. They were sized as annotations on a row whose only real content was the
                // name and the time, but on a results board the club and the place are half of
                // what a spectator is there for, and a delta nobody can read from a seat is a
                // column of wasted width. Colour still carries the hierarchy — the club stays
                // `th_text` against the name's `row_text` — so matching the sizes does not
                // make them compete.
                // It shrinks before it ellipsises, like the name (`L-17`), and is held to
                // [clubMax] so a long club cannot squeeze the name beside it off the line.
                if (settings.showClub && r.club.isNotEmpty()) {
                    AutoSizeText(
                        r.club,
                        Modifier.padding(start = 6.dp * scale).widthIn(max = clubMax),
                        color = colors.thText,
                        maxSize = size.asSp(),
                        minSize = shrinkFloor(size).asSp(),
                        textAlign = TextAlign.End,
                    )
                }
            }
            // L-06: relay members, dimmed, under the name — the first thing given up when the
            // lanes get tight.
            if (settings.showName && showsAlt && r.alt.isNotEmpty()) {
                AutoSizeText(
                    r.alt,
                    Modifier.fillMaxWidth(),
                    color = colors.thText,
                    maxSize = (size * 0.62f).asSp(),
                    minSize = shrinkFloor(size * 0.62f).asSp(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The time and whatever shares its line sit **together**, at the left of the
                // line, rather than one at each end of it: a delta pushed against the place
                // column reads as belonging to the place, and a lap there reads as a rank. The
                // slack goes between that pair and the place, which stays hard right. The time
                // is the only weighted cell, so it can never squeeze what follows it off the
                // row — it is capped at what the pair has left — but with `fill = false` it
                // takes only the width it needs and the delta starts a gutter later.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    TimeText(
                        r.time,
                        r.timeStyle,
                        r.lockEdge,
                        size * 0.92f * lineFit,
                        Modifier.weight(1f, fill = false),
                        TextAlign.Start,
                    )
                    // L-23: one cell, two tenants. There is no delta *column* to centre in here
                    // — the second line is time · delta · place laid out in flow — so the cell
                    // is sized to whichever of the two is in it, and the colour and the swap
                    // carry the meaning on their own.
                    if (settings.showDelta) {
                        Spacer(Modifier.width(CellGutter * scale))
                        if (r.lap != null) {
                            LapText(r.lap, size * lineFit)
                        } else {
                            DeltaText(r.deltaSeconds, r.deltaBetter, size * lineFit)
                        }
                    }
                }
                if (settings.showPosition) PlaceText(r.place, size, Modifier.width(PlaceW * scale))
            }
        }
    }
}

// ── landscape: the full table, font scaled to lane count (L-16) ──────────────

@Composable
private fun LandscapeGrid(
    rows: List<GridRow>,
    settings: MeetSettings,
    labels: Map<String, String>,
    laps: LapSettings,
    lapsWord: String,
    modifier: Modifier,
) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val density = LocalDensity.current
    // L-23: the delta title goes entirely while the lap count is on. For most of a heat that
    // column holds lengths, and a `Δ` over a column of small integers reads as a claim about
    // them. It stays gone through the results too — a header that appeared at the finish would
    // be the moving header L-23 rejects.
    val showDeltaHeader = settings.showDeltaHeader && !laps.show
    val anyHeader =
        settings.showLaneHeader || (settings.showName && settings.showNameHeader) ||
            (settings.showClub && settings.showClubHeader) ||
            settings.showTimeHeader || (settings.showDelta && showDeltaHeader) ||
            (settings.showPosition && settings.showPositionHeader)
    // The one place the titles and the rows read their widths from, so every title sits over
    // its column whichever of them the meet has hidden.
    val cols = remember(settings.showName, settings.showClub, settings.showDelta, settings.showPosition) {
        BoardColumns.of(settings.showName, settings.showClub, settings.showDelta, settings.showPosition)
    }
    // What the column titles cost, measured while they are being drawn and kept after they
    // go — which is exactly the number needed to decide whether to bring them back. Measured
    // rather than declared: it is a line of text on this device at this setting, and a
    // constant tuned on one phone is wrong on the rest.
    var headerBand by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val count = rows.size.coerceAtLeast(1)
        // Sized once with the header's band withheld; if that comes out cramped the titles go
        // and the rows are sized again over the whole height. See `BoardSizing.showsHeader`
        // for why that cannot oscillate, 56 cap and all.
        val showsHeader = BoardSizing.showsHeader(anyHeader, maxHeight.value, headerBand.value, count)
        val lanesHeight = maxHeight - (if (showsHeader) headerBand else 0.dp)
        fun roomDp(fraction: Float) = maxWidth * fraction - CellPad * 2
        fun room(fraction: Float) = with(density) { roomDp(fraction).toPx() }
        val deltaTexts = if (settings.showDelta) BoardSizing.deltaTexts(rows.map(::deltaCellText)) else emptyList()
        // Height sets the type, but a tall, narrow board (an upright tablet) would set it
        // bigger than the time and delta columns can hold even at their floor; the width caps
        // it there rather than let them clip.
        val timePerDp = rememberWidthPerDp(listOf(BoardSizing.TIME_TEMPLATE), fonts.timing)
        val deltaPerDp = rememberWidthPerDp(deltaTexts, fonts.timing)
        val widthCap = minOf(
            BoardSizing.widthCap(roomDp(cols.time).value, timePerDp, CELL_SHARE),
            if (settings.showDelta) {
                BoardSizing.widthCap(
                    roomDp(cols.delta).value,
                    deltaPerDp,
                    CELL_SHARE,
                )
            } else {
                Float.MAX_VALUE
            },
        )
        // Worked out in dp and converted once, here: everything below is a fraction of this,
        // so a font-size setting over 1 cannot flip a floor above its ceiling (`parity.md` §8).
        val size = BoardSizing.rowFont(lanesHeight.value, count, widthCap).dp
        val rowHeight: Dp = lanesHeight / count
        val cell = size * CELL_SHARE

        // L-17: time and delta take one size per column, fitted to the widest thing the column
        // can hold rather than to what it holds now — see `BoardSizing.TIME_TEMPLATE`. The
        // place too: it takes the row font whole, which a 6% column cannot always hold.
        val timeFit = rememberColumnFit(listOf(BoardSizing.TIME_TEMPLATE), fonts.timing, cell, room(cols.time))
        val deltaFit = if (settings.showDelta) {
            rememberColumnFit(
                deltaTexts,
                fonts.timing,
                cell,
                room(cols.delta),
            )
        } else {
            1f
        }
        val laneFit = rememberColumnFit(
            listOf("8".repeat(count.toString().length)),
            fonts.family,
            size,
            with(density) { (maxWidth * cols.lane - PlaceMargin).toPx() },
        )
        val placeFit =
            if (settings.showPosition) {
                rememberColumnFit(
                    listOf(BoardSizing.placeTemplate(count)),
                    fonts.digits,
                    size,
                    with(density) { (maxWidth * cols.place - PlaceMargin).toPx() },
                    FontWeight.Bold,
                )
            } else {
                1f
            }

        Column(Modifier.fillMaxSize()) {
            if (showsHeader) {
                Row(
                    Modifier.fillMaxWidth().background(colors.thBg).padding(vertical = 3.dp)
                        .onSizeChanged { with(density) { headerBand = it.height.toDp() } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Fixed at 13dp rather than 13sp: the titles sit above a board that sizes
                    // itself from the height it has, and a header that grew with the font-size
                    // setting would take that height from the lanes underneath it. Not grown
                    // with the rows either: the band is what the rows are sized around, and a
                    // band that followed the rows would be measuring its own answer.
                    val thSize = with(density) { 13.dp.toSp() }
                    val th: @Composable (String, Boolean, Float, TextAlign) -> Unit = { text, show, weight, align ->
                        Text(
                            if (show) text else "",
                            color = colors.thText,
                            fontSize = thSize,
                            fontFamily = fonts.family,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = align,
                            modifier = Modifier.weight(weight).padding(horizontal = CellPad),
                        )
                    }
                    th(labels["lane"].orEmpty(), settings.showLaneHeader, cols.lane, TextAlign.Center)
                    if (cols.name >
                        0f
                    ) {
                        th(labels["name"].orEmpty(), settings.showNameHeader, cols.name, TextAlign.Start)
                    }
                    if (cols.spacer > 0f) Spacer(Modifier.weight(cols.spacer))
                    if (cols.club >
                        0f
                    ) {
                        th(labels["club"].orEmpty(), settings.showClubHeader, cols.club, TextAlign.Center)
                    }
                    th(labels["time"].orEmpty(), settings.showTimeHeader, cols.time, TextAlign.Center)
                    if (cols.delta > 0f) th(labels["delta"].orEmpty(), showDeltaHeader, cols.delta, TextAlign.Center)
                    if (cols.place > 0f) {
                        th(labels["place"].orEmpty(), settings.showPositionHeader, cols.place, TextAlign.Center)
                    }
                }
            }
            val still = stillLanes(rows)
            rows.forEachIndexed { i, r ->
                val description = spoken(r, settings, labels, lapsWord)
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .background(if (i % 2 == 0) colors.rowOdd else colors.rowEven)
                        .clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LaneNumber(r.lane, r.pulsing, size * laneFit, Modifier.weight(cols.lane))
                    if (still[i]) {
                        StillWater(
                            size * 0.1f,
                            Modifier.weight(1f - cols.lane).fillMaxHeight().padding(horizontal = CellPad),
                        )
                        return@Row
                    }
                    if (cols.name > 0f) {
                        Column(Modifier.weight(cols.name).padding(horizontal = CellPad)) {
                            AutoSizeText(
                                r.name,
                                Modifier.fillMaxWidth(),
                                maxSize = cell.asSp(),
                                minSize = shrinkFloor(cell, size * 0.5f).asSp(),
                            )
                            if (r.alt.isNotEmpty()) {
                                AutoSizeText(
                                    r.alt,
                                    Modifier.fillMaxWidth(),
                                    color = colors.thText,
                                    maxSize = (size * 0.5f).asSp(),
                                    minSize = shrinkFloor(size * 0.5f).asSp(),
                                )
                            }
                        }
                    }
                    if (cols.spacer > 0f) Spacer(Modifier.weight(cols.spacer))
                    if (cols.club > 0f) {
                        AutoSizeText(
                            r.club,
                            Modifier.weight(cols.club).padding(horizontal = CellPad),
                            maxSize = cell.asSp(),
                            minSize = shrinkFloor(cell).asSp(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    TimeText(
                        r.time,
                        r.timeStyle,
                        r.lockEdge,
                        cell * timeFit,
                        Modifier.weight(cols.time).padding(horizontal = CellPad),
                        TextAlign.Center,
                    )
                    // L-23: the lap is centred in the column the delta shares with it, and
                    // the header above them both says nothing while it may hold either. Both
                    // take the column's one size, so the column never goes ragged.
                    if (cols.delta > 0f) {
                        Box(
                            Modifier.weight(cols.delta).padding(horizontal = CellPad),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r.lap != null) {
                                LapText(r.lap, cell * deltaFit)
                            } else {
                                DeltaText(r.deltaSeconds, r.deltaBetter, cell * deltaFit)
                            }
                        }
                    }
                    // The place takes the row font whole — it is one character, it is the
                    // answer, and it has a column to itself — unless the column cannot hold it.
                    if (cols.place > 0f) {
                        PlaceText(r.place, size * placeFit, Modifier.weight(cols.place), Arrangement.Center)
                    }
                }
            }
        }
    }
}

/** What a row's delta cell holds, as text — the lap while there is one (`L-23`). */
private fun deltaCellText(r: GridRow): String = r.lap?.text ?: DeltaFormat.text(r.deltaSeconds)

/**
 * One size factor for a whole column (`L-17`): 1 if the widest of [texts] fits [roomPx] at
 * [size], else the ratio, floored at half. Measured with `TextMeasurer` only when one of its
 * inputs changes — the size, the width, the face, or the texts themselves, which for the time
 * column is a template and never the ticking value.
 */
@Composable
private fun rememberColumnFit(
    texts: List<String>,
    font: FontFamily,
    size: Dp,
    roomPx: Float,
    weight: FontWeight? = null,
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, font, size, roomPx, weight, density) {
        val style = TextStyle(fontFamily = font, fontSize = with(density) { size.toSp() }, fontWeight = weight)
        val need = texts.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
        BoardSizing.columnFit(roomPx, need.toFloat())
    }
}

/**
 * How wide the widest of [texts] is per dp of type, measured once at a reference size: what
 * `BoardSizing.widthCap` needs to know before the row font exists.
 */
@Composable
private fun rememberWidthPerDp(texts: List<String>, font: FontFamily): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, font, density) {
        if (texts.isEmpty()) return@remember 0f
        val ref = 100.dp
        val style = TextStyle(fontFamily = font, fontSize = with(density) { ref.toSp() })
        val px = texts.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
        with(density) { px.toDp() } / ref
    }
}

/** A full-table time or delta cell's size, as a share of the row font. */
private const val CELL_SHARE = 0.85f

/**
 * `L-17`'s floor for a cell set at [max]: [min], but never under 10dp (`BoardSizing.shrinkFloor`).
 * Worked in dp and converted back, so the floor and the ceiling come off the same pinned unit.
 */
private fun shrinkFloor(max: Dp, min: Dp = max * BoardSizing.FIT_FLOOR): Dp =
    BoardSizing.shrinkFloor(max.value, min.value).dp

/**
 * The one `dp` → `sp` conversion a board size gets, at the cell that draws it. Every product
 * above is taken in `dp`: since Android 14 the font-size setting scales `sp` non-linearly —
 * large type less than small — so `size * 0.85f * fit` worked out in `sp` does not shrink the
 * pixels by `fit`, and a column fitted that way clips at 1.3×.
 */
@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { toSp() }

/**
 * The gutter between the time and the delta cell sharing its line in portrait (`L-15`). Small
 * enough that the two read as one group and wide enough that `2:24.28+0.18` never does.
 */
private val CellGutter = 8.dp

/** A full-table cell's padding on either side. */
private val CellPad = 6.dp

/**
 * What the lane and place columns keep clear of their edges when fitted. They have no padding
 * — the row font fills them — and a seven-segment `1` draws at the right of its advance.
 */
private val PlaceMargin = 4.dp

/** The compact row's lane-number column, end padding and place column, all × the row's scale. */
private val LaneW = 34.dp
private val RowEndPad = 8.dp
private val PlaceW = 56.dp

/** The most of a compact row's first line the club may take; past it the club shrinks. */
private const val CLUB_SHARE = 0.4f

/**
 * The compact row's line two (`L-15`): one factor for the time and the delta both, so the
 * template time and the widest delta fit [roomPx] side by side. Measured only when an input
 * changes, never on the clock's tick.
 */
@Composable
private fun rememberLineFit(font: FontFamily, size: Dp, roomPx: Float, deltaTexts: List<String>): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(font, size, roomPx, deltaTexts, density) {
        fun width(text: String, s: Dp) = measurer.measure(
            text,
            TextStyle(fontFamily = font, fontSize = with(density) { s.toSp() }),
            maxLines = 1,
            softWrap = false,
        ).size.width
        val time = width(BoardSizing.TIME_TEMPLATE, size * 0.92f)
        val delta = deltaTexts.maxOfOrNull { width(it, size) } ?: 0
        BoardSizing.columnFit(roomPx, (time + delta).toFloat())
    }
}

// ── cells ────────────────────────────────────────────────────────────────────

/**
 * L-12's pulse: the lane number cycles row colour → timing colour → row colour once a
 * second while the lane runs without a clock to show. A cycle that has started finishes,
 * so stopping every lane on the same re-base cannot flick the column.
 */
@Composable
private fun LaneNumber(text: String, pulsing: Boolean, size: Dp, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val color = remember { Animatable(colors.rowText) }
    val pulsingNow by rememberUpdatedState(pulsing)
    LaunchedEffect(colors) {
        while (isActive) {
            snapshotFlow { pulsingNow }.first { it }
            do {
                color.animateTo(colors.time, tween(500))
                color.animateTo(colors.rowText, tween(500))
            } while (pulsingNow)
        }
    }
    Text(
        text,
        color = color.value,
        fontSize = size.asSp(),
        fontFamily = LocalBoardFonts.current.family,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/**
 * L-25: an empty lane, drawn as the surface of water nobody is swimming in. One faint sine
 * line across the row, fading out at both ends, that drifts a wavelength every ten seconds —
 * slow enough to sit under the eye rather than catch it. The phase comes off the wall clock,
 * so empty lanes side by side move as one surface whenever each one emptied. Remove
 * animations stills it (`reduceMotion`); it says the same thing standing.
 *
 * Not the lane number: that already pulses for a lane waiting on its clock (L-12), and a
 * second animation there would read as the first.
 */
@Composable
private fun StillWater(amplitude: Dp, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val still = reduceMotion()
    var phase by remember { mutableFloatStateOf(0f) }
    if (!still) {
        LaunchedEffect(Unit) {
            while (isActive) {
                phase = (System.currentTimeMillis() % STILL_WATER_PERIOD_MS) / STILL_WATER_PERIOD_MS.toFloat()
                delay(50)
            }
        }
    }
    val line = colors.thText.copy(alpha = colors.thText.alpha * 0.35f)
    Canvas(modifier.clearAndSetSemantics { }) {
        val amp = amplitude.toPx()
        val wavelength = maxOf(36.dp.toPx(), amp * 18f)
        val mid = size.height / 2f
        val path = Path()
        var x = 0f
        while (x <= size.width) {
            val y = mid + amp * sin((x / wavelength - phase) * 2f * PI.toFloat())
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 2f
        }
        drawPath(
            path,
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.15f to line,
                0.85f to line,
                1f to Color.Transparent,
            ),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

private const val STILL_WATER_PERIOD_MS = 10_000L

/**
 * L-11: a running time is dimmed; on the stop edge it flashes and settles to the timing
 * colour; running again drops the lock at once. Results reuse the settled look (R-09).
 *
 * The flash starts from `row_text` rather than a fixed white. White was from a board that
 * was only ever dark; on the light one (`P-15`) it is a flash that cannot be seen at all,
 * and this is the one moment on the board that has to be. `row_text` is the highest-contrast
 * colour the palette has against the row it is drawn on, whichever way round that row is.
 */
@Composable
private fun TimeText(text: String, style: TimeStyle, lockEdge: Int, size: Dp, modifier: Modifier, align: TextAlign) {
    val colors = LocalBoardColors.current
    val target = if (style == TimeStyle.RUNNING) colors.timeRunning else colors.time
    val color = remember { Animatable(target) }
    LaunchedEffect(style, lockEdge, colors) {
        if (style == TimeStyle.LOCKED && lockEdge > 0) {
            color.snapTo(colors.rowText)
            color.animateTo(colors.time, tween(800))
        } else {
            color.snapTo(target)
        }
    }
    Text(
        text,
        color = color.value,
        fontSize = size.asSp(),
        fontFamily = LocalBoardFonts.current.timing,
        textAlign = align,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/**
 * Set at its column's one size (`L-17`) rather than fitted per value: a delta that shrank on
 * its own would leave the column ragged, one lane's figure smaller than the next.
 */
@Composable
private fun DeltaText(seconds: Double?, better: Boolean?, size: Dp) {
    val colors = LocalBoardColors.current
    val color = when (better) {
        true -> colors.deltaBetter
        false -> colors.deltaWorse
        null -> colors.rowText
    }
    Text(
        DeltaFormat.text(seconds),
        color = color,
        fontSize = size.asSp(),
        fontFamily = LocalBoardFonts.current.timing,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.End,
    )
}

/**
 * `L-23`'s tenant of the delta cell: the lengths a lane has swum, or what is left of them.
 *
 * It keeps the cell's timing face and changes only its colour, which is the whole of what says
 * which of the two numbers is on screen. `header_label` is the accent the EVENT and HEAT words
 * take — *this is a label, not a number you race against* — and on the final stretch it moves
 * to `time`, the colour a stopped chrono has.
 *
 * **No animation.** An earlier version pulsed the final stretch and it was removed on purpose:
 * the number is already the information, and a static colour change is the whole effect.
 */
@Composable
private fun LapText(lap: LapCount, size: Dp) {
    val colors = LocalBoardColors.current
    Text(
        lap.text,
        color = if (lap.isFinal) colors.time else colors.headerLabel,
        fontSize = size.asSp(),
        fontFamily = LocalBoardFonts.current.timing,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
    )
}

/**
 * A place is prefixed `#`; no place means an empty cell, no dash and no `#` (L-15, R-07).
 *
 * It sits under its own centred column header in the landscape table, so it is centred
 * there too; in the portrait row there is no header and it is the last thing on the line,
 * so it stays hard right.
 */
@Composable
private fun PlaceText(
    place: String,
    size: Dp,
    modifier: Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.End,
) {
    val colors = LocalBoardColors.current
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        if (place.isNotEmpty()) {
            Text(
                "#",
                color = colors.headerLabel.copy(alpha = 0.5f),
                fontSize = size.asSp(),
                fontFamily = LocalBoardFonts.current.digits,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                place,
                color = colors.headerLabel,
                fontSize = size.asSp(),
                fontWeight = FontWeight.Bold,
                fontFamily = LocalBoardFonts.current.digits,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ── header ───────────────────────────────────────────────────────────────────

/**
 * Portrait's board header: EVENT and HEAT as a small label over a large value (`L-01`),
 * the event name (`L-02`) and the wall clock (`L-03`).
 *
 * It draws no background and no hairline of its own. That band was `mobile.html`'s
 * `border-bottom`, separating two documents that had to line up as one screen (§0.4);
 * under a real app bar it is simply a second strip of chrome for one board.
 */
@Composable
fun BoardHeader(
    eventLabel: String,
    event: String,
    heatLabel: String,
    heat: String,
    eventName: String,
    clock: String?,
    /** What this band costs is measured here and kept once it moves into the app bar. */
    metrics: BoardMetrics,
) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val height = (cfg.screenHeightDp * 0.085f).coerceIn(52f, 92f).dp
    val t = headerType(height)
    BoardType {
        Row(
            Modifier.fillMaxWidth().heightIn(min = height).padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { with(density) { metrics.headerBand = it.height.toDp() } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderCell(eventLabel, event, t.label, t.value, fonts.family, fonts.digits)
            HeaderCell(heatLabel, heat, t.label, t.value, fonts.family, fonts.digits)
            // Centred in the slot between the header cells and the clock: pinned left between
            // two items sitting at the edges, it read as floating rather than placed.
            AutoSizeText(
                eventName,
                Modifier.weight(1f),
                color = colors.headerValue,
                fontFamily = fonts.family,
                maxSize = t.name,
                minSize = t.nameFloor,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (clock != null) {
                Text(
                    clock,
                    // The accent blue, like the EVENT/HEAT words, as on every board.
                    color = colors.headerLabel,
                    fontSize = t.value,
                    fontFamily = fonts.digits,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The four sizes a board header needs, as fractions of the height it has. */
private data class HeaderType(val label: TextUnit, val value: TextUnit, val name: TextUnit, val nameFloor: TextUnit)

/**
 * The header takes its type from the height it has, the way the lane rows take theirs from
 * the height they share (`L-15`).
 *
 * Every size goes through `toSp()`, which divides out the device's font-size setting, so a
 * reader who has turned text up moves the chrome around the board and not the board. They
 * were fixed `sp` before: at 2x the EVENT and HEAT words grew until they had eaten the row
 * and squeezed the event name down to an ellipsis, which is a board that stops reporting
 * the heat in order to name it. The lane rows below still follow the setting — see
 * `parity.md` §8, where whether they should is still open.
 */
@Composable
private fun headerType(height: Dp): HeaderType = with(LocalDensity.current) {
    HeaderType(
        label = (height * 0.17f).toSp(),
        value = (height * 0.35f).toSp(),
        name = (height * 0.25f).toSp(),
        nameFloor = (height * 0.13f).toSp(),
    )
}

/**
 * The same three rows, folded onto the single line a top app bar gives you. `L-01`'s
 * label-over-value cannot survive a bar one row high, so the word sits beside its number.
 */
@Composable
fun BoardBarHeaderRow(
    eventLabel: String,
    event: String,
    heatLabel: String,
    heat: String,
    eventName: String,
    clock: String?,
    /** P-11's server name, where the app bar's subtitle slot is taken by this row. */
    server: String? = null,
) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    // A top app bar is a fixed 64dp, so that is the height this row is sized from.
    val t = headerType(BarHeight)
    BoardType {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InlineCell(eventLabel, event, t.label, t.value, fonts.family, fonts.digits)
            InlineCell(heatLabel, heat, t.label, t.value, fonts.family, fonts.digits)
            AutoSizeText(
                eventName,
                Modifier.weight(1f),
                color = colors.headerValue,
                fontFamily = fonts.family,
                maxSize = t.name,
                minSize = t.nameFloor,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (server != null) {
                Text(
                    server,
                    color = colors.thText,
                    fontSize = t.label,
                    fontFamily = fonts.family,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (clock != null) {
                Text(
                    clock,
                    // The accent blue, like the EVENT/HEAT words, as on every board.
                    color = colors.headerLabel,
                    fontSize = t.value,
                    fontFamily = fonts.digits,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Material's top app bar height, which the landscape header row is measured against. */
private val BarHeight = 64.dp

/** The word and its number read as one thing, and say nothing at all before a number arrives. */
@Composable
private fun HeaderCell(
    label: String,
    value: String,
    labelSize: TextUnit,
    valueSize: TextUnit,
    labelFont: FontFamily,
    valueFont: FontFamily,
) {
    val colors = LocalBoardColors.current
    val spokenValue = if (value.isBlank()) "" else "$label $value"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { if (spokenValue.isNotEmpty()) contentDescription = spokenValue },
    ) {
        Text(
            label,
            color = colors.headerLabel,
            fontSize = labelSize,
            fontFamily = labelFont,
            maxLines = 1,
            letterSpacing = 1.sp,
        )
        // `header_value`, as on every other board: the accent blue belongs to the word, and the
        // two colours are what tell it from its number. Seven-segment face; give the tall
        // glyphs their line.
        Text(
            value.ifEmpty {
                " "
            },
            color = colors.headerValue,
            fontSize = valueSize,
            lineHeight = valueSize * 1.25f,
            fontFamily = valueFont,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun InlineCell(
    label: String,
    value: String,
    labelSize: TextUnit,
    valueSize: TextUnit,
    labelFont: FontFamily,
    valueFont: FontFamily,
) {
    val colors = LocalBoardColors.current
    val spokenValue = if (value.isBlank()) "" else "$label $value"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clearAndSetSemantics { if (spokenValue.isNotEmpty()) contentDescription = spokenValue },
    ) {
        Text(
            label,
            color = colors.headerLabel,
            fontSize = labelSize,
            fontFamily = labelFont,
            maxLines = 1,
            letterSpacing = 1.sp,
        )
        Text(
            value.ifEmpty {
                " "
            },
            color = colors.headerValue,
            fontSize = valueSize,
            fontFamily = valueFont,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
