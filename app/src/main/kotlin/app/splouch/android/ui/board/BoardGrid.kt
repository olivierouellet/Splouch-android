package app.splouch.android.ui.board

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.splouch.android.ui.theme.AutoSizeText
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.android.ui.theme.LocalBoardFonts
import app.splouch.core.board.DeltaFormat
import app.splouch.core.board.ScoreboardState.TimeStyle
import app.splouch.core.wire.MeetSettings
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
)

/** The six-column board shared by the Scoreboard and Results tabs (app.md L-04..L-09, L-15..L-17, R-04). */
@Composable
fun BoardGrid(
    rows: List<GridRow>,
    settings: MeetSettings,
    labels: Map<String, String>,
    landscape: Boolean,
    metrics: BoardMetrics,
    /** Whether the app bar is already carrying the `EVENT`/`HEAT` row (`L-15`, `L-16`). */
    headerInBar: Boolean,
    modifier: Modifier = Modifier,
) {
    BoardType {
        if (landscape) LandscapeGrid(rows, settings, labels, modifier)
        else PortraitGrid(rows, settings, labels, metrics, headerInBar, modifier)
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
 * number, which is what `L-09`'s blank row means.
 */
private fun spoken(r: GridRow, settings: MeetSettings, labels: Map<String, String>): String {
    fun word(key: String) = labels[key].orEmpty()
    val parts = mutableListOf("${word("lane")} ${r.lane}".trim())
    if (settings.showName && r.name.isNotBlank()) parts += r.name
    if (settings.showName && r.alt.isNotBlank()) parts += r.alt
    if (settings.showClub && r.club.isNotBlank()) parts += "${word("club")} ${r.club}".trim()
    if (r.time.isNotBlank()) parts += "${word("time")} ${r.time}".trim()
    if (settings.showDelta && r.deltaSeconds != null) parts += "${word("delta")} ${DeltaFormat.text(r.deltaSeconds)}".trim()
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
        val base = with(density) { (share * 0.26f).coerceIn(13.dp, 24.dp).toSp() }

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
        val scale = (if (want > 0.dp) share / want else 1f).coerceIn(PortraitTypeFloor, 1f)
        val rowHeight = maxOf(share, want * scale)

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
                        rows.first(), settings, base, scale = 1f, showsAlt = false,
                        modifier = Modifier.onSizeChanged { with(density) { metrics.laneIdeal = it.height.toDp() } },
                    )
                    // Real content, not a stand-in: the height of a relay row is the height
                    // of the words actually in it.
                    rows.firstOrNull { it.alt.isNotEmpty() }?.let { relay ->
                        PortraitRow(
                            relay, settings, base, scale = 1f, showsAlt = true,
                            modifier = Modifier.onSizeChanged { with(density) { metrics.laneIdealWithAlt = it.height.toDp() } },
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, r ->
                    val description = spoken(r, settings, labels)
                    PortraitRow(
                        r, settings, base, scale, showsAlt,
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
 */
@Composable
private fun PortraitRow(
    r: GridRow,
    settings: MeetSettings,
    base: TextUnit,
    scale: Float,
    showsAlt: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val size = base * scale
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LaneNumber(r.lane, r.pulsing, size, Modifier.width(34.dp * scale))
        Column(Modifier.weight(1f).padding(end = 8.dp * scale)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // The floor comes off the same pinned size as the ceiling — see `AutoSizeText`.
                if (settings.showName) AutoSizeText(r.name, Modifier.weight(1f), maxSize = size, minSize = size * 0.6f) else Box(Modifier.weight(1f))
                // Club, delta and place read at the name's size rather than a quarter under
                // it. They were sized as annotations on a row whose only real content was the
                // name and the time, but on a results board the club and the place are half of
                // what a spectator is there for, and a delta nobody can read from a seat is a
                // column of wasted width. Colour still carries the hierarchy — the club stays
                // `th_text` against the name's `row_text` — so matching the sizes does not
                // make them compete.
                if (settings.showClub) Text(r.club, color = colors.thText, fontSize = size, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontFamily = fonts.family, textAlign = TextAlign.End, modifier = Modifier.padding(start = 6.dp * scale))
            }
            // L-06: relay members, dimmed, under the name — the first thing given up when the
            // lanes get tight.
            if (settings.showName && showsAlt && r.alt.isNotEmpty()) {
                Text(r.alt, color = colors.thText, fontSize = size * 0.62f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fonts.family)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(r.time, r.timeStyle, r.lockEdge, size * 0.92f, Modifier.weight(1f), TextAlign.Start)
                if (settings.showDelta) DeltaText(r.deltaSeconds, r.deltaBetter, size, size * 0.7f)
                if (settings.showPosition) PlaceText(r.place, size, Modifier.width(56.dp * scale))
            }
        }
    }
}

// ── landscape: the full table, font scaled to lane count (L-16) ──────────────

@Composable
private fun LandscapeGrid(rows: List<GridRow>, settings: MeetSettings, labels: Map<String, String>, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val density = LocalDensity.current
    val anyHeader = settings.showLaneHeader || (settings.showName && settings.showNameHeader) || (settings.showClub && settings.showClubHeader) ||
        settings.showTimeHeader || (settings.showDelta && settings.showDeltaHeader) || (settings.showPosition && settings.showPositionHeader)
    // What the column titles cost, measured while they are being drawn and kept after they
    // go — which is exactly the number needed to decide whether to bring them back. Measured
    // rather than declared: it is a line of text on this device at this setting, and a
    // constant tuned on one phone is wrong on the rest.
    var headerBand by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val count = rows.size.coerceAtLeast(1)
        fun font(available: Dp): Dp = (available * TypeShare / count).coerceIn(10.dp, 32.dp)
        // Sized once with the header's band withheld; if that comes out cramped the titles go
        // and the rows are sized again over the whole height. It cannot oscillate: the
        // predicate reads the *cached* band and never the height the answer changes, and
        // dropping the header only ever makes the type bigger.
        val withHeader = font(maxHeight - headerBand)
        val showsHeader = anyHeader && (headerBand <= 0.dp || withHeader >= HeaderFloor)
        val size = with(density) { (if (showsHeader) withHeader else font(maxHeight)).toSp() }
        val rowHeight: Dp = (maxHeight - (if (showsHeader) headerBand else 0.dp)) / count
        Column(Modifier.fillMaxSize()) {
            if (showsHeader) {
                Row(
                    Modifier.fillMaxWidth().background(colors.thBg).padding(vertical = 3.dp)
                        .onSizeChanged { with(density) { headerBand = it.height.toDp() } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Fixed at 13dp rather than 13sp: the titles sit above a board that sizes
                    // itself from the height it has, and a header that grew with the font-size
                    // setting would take that height from the lanes underneath it.
                    val thSize = with(density) { 13.dp.toSp() }
                    val th: @Composable (String, Boolean, Modifier, TextAlign) -> Unit = { text, show, m, align ->
                        Text(if (show) text else "", color = colors.thText, fontSize = thSize, fontFamily = fonts.family, maxLines = 1, textAlign = align, modifier = m.padding(horizontal = 6.dp))
                    }
                    th(labels["lane"].orEmpty(), settings.showLaneHeader, Modifier.width(LaneW), TextAlign.Center)
                    if (settings.showName) th(labels["name"].orEmpty(), settings.showNameHeader, Modifier.weight(1f), TextAlign.Start)
                    if (settings.showClub) th(labels["club"].orEmpty(), settings.showClubHeader, Modifier.weight(0.6f), TextAlign.Center)
                    th(labels["time"].orEmpty(), settings.showTimeHeader, Modifier.width(TimeW), TextAlign.Center)
                    if (settings.showDelta) th(labels["delta"].orEmpty(), settings.showDeltaHeader, Modifier.width(DeltaW), TextAlign.Center)
                    if (settings.showPosition) th(labels["place"].orEmpty(), settings.showPositionHeader, Modifier.width(PlaceW), TextAlign.Center)
                }
            }
            rows.forEachIndexed { i, r ->
                val description = spoken(r, settings, labels)
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .background(if (i % 2 == 0) colors.rowOdd else colors.rowEven)
                        .clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LaneNumber(r.lane, r.pulsing, size, Modifier.width(LaneW))
                    if (settings.showName) {
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            AutoSizeText(r.name, Modifier.fillMaxWidth(), maxSize = size * 0.85f, minSize = size * 0.5f)
                            if (r.alt.isNotEmpty()) Text(r.alt, color = colors.thText, fontSize = size * 0.5f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fonts.family)
                        }
                    }
                    if (settings.showClub) Text(r.club, color = colors.rowText, fontSize = size * 0.85f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fonts.family,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f).padding(horizontal = 6.dp))
                    TimeText(r.time, r.timeStyle, r.lockEdge, size * 0.85f, Modifier.width(TimeW), TextAlign.Center)
                    // Shrink rather than wrap: the column is fixed and the delta is now set
                    // at the name's size, so a four-lane board at the row-font cap can ask
                    // for more width than it has. A delta on two lines is not a delta.
                    if (settings.showDelta) Box(Modifier.width(DeltaW), contentAlignment = Alignment.Center) { DeltaText(r.deltaSeconds, r.deltaBetter, size * 0.85f, size * 0.6f) }
                    // The place takes the row font whole: it is one character, it is the
                    // answer, and it has a column to itself.
                    if (settings.showPosition) PlaceText(r.place, size, Modifier.width(PlaceW), Arrangement.Center)
                }
            }
        }
    }
}

/**
 * How tall a landscape row's type may be, as a fraction of the height that row is given.
 *
 * It was 0.48, which left over half of every row as leading: on a six-lane board the rows
 * are 55dp tall and the numbers a spectator came to read were set at 26. A landscape row
 * has **no vertical padding at all**, so this — not padding — is the whole of what holds
 * the type down. 0.55 still leaves room for a name with an alt line under it (0.85 + 0.5
 * of the row font, so 74% of the row).
 */
private const val TypeShare = 0.55f

/**
 * Below this the column titles cost more height than their words are worth, so the table
 * drops them and gives the band back to the lanes.
 */
private val HeaderFloor = 14.dp

private val LaneW = 52.dp
private val TimeW = 130.dp
// Widened with the delta's own size: it is set from the row font now, not two thirds of it.
private val DeltaW = 110.dp
private val PlaceW = 56.dp

// ── cells ────────────────────────────────────────────────────────────────────

/**
 * L-12's pulse: the lane number cycles row colour → timing colour → row colour once a
 * second while the lane runs without a clock to show. A cycle that has started finishes,
 * so stopping every lane on the same re-base cannot flick the column.
 */
@Composable
private fun LaneNumber(text: String, pulsing: Boolean, size: TextUnit, modifier: Modifier) {
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
    Text(text, color = color.value, fontSize = size, fontFamily = LocalBoardFonts.current.family, textAlign = TextAlign.Center, maxLines = 1, modifier = modifier)
}

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
private fun TimeText(text: String, style: TimeStyle, lockEdge: Int, size: TextUnit, modifier: Modifier, align: TextAlign) {
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
    Text(text, color = color.value, fontSize = size, fontFamily = LocalBoardFonts.current.timing, textAlign = align, maxLines = 1, softWrap = false, modifier = modifier)
}

/** [minSize] under [size] lets a fixed column shrink the delta rather than wrap it. */
@Composable
private fun DeltaText(seconds: Double?, better: Boolean?, size: TextUnit, minSize: TextUnit = size) {
    val colors = LocalBoardColors.current
    val color = when (better) { true -> colors.deltaBetter; false -> colors.deltaWorse; null -> colors.rowText }
    val text = DeltaFormat.text(seconds)
    val font = LocalBoardFonts.current.timing
    if (minSize < size) {
        AutoSizeText(text, color = color, fontFamily = font, maxSize = size, minSize = minSize, textAlign = TextAlign.End)
    } else {
        Text(text, color = color, fontSize = size, fontFamily = font, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
    }
}

/**
 * A place is prefixed `#`; no place means an empty cell, no dash and no `#` (L-15, R-07).
 *
 * It sits under its own centred column header in the landscape table, so it is centred
 * there too; in the portrait row there is no header and it is the last thing on the line,
 * so it stays hard right.
 */
@Composable
private fun PlaceText(place: String, size: TextUnit, modifier: Modifier, arrangement: Arrangement.Horizontal = Arrangement.End) {
    val colors = LocalBoardColors.current
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        if (place.isNotEmpty()) {
            Text("#", color = colors.headerLabel.copy(alpha = 0.5f), fontSize = size, fontFamily = LocalBoardFonts.current.digits, maxLines = 1)
            Text(place, color = colors.headerLabel, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = LocalBoardFonts.current.digits, maxLines = 1)
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
    eventLabel: String, event: String, heatLabel: String, heat: String, eventName: String, clock: String?,
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
            eventName, Modifier.weight(1f), color = colors.headerValue, fontFamily = fonts.family,
            maxSize = t.name, minSize = t.nameFloor, textAlign = TextAlign.Center, maxLines = 2,
        )
        if (clock != null) {
            Text(clock, color = colors.headerValue, fontSize = t.value, fontFamily = fonts.digits, maxLines = 1, softWrap = false)
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
    eventLabel: String, event: String, heatLabel: String, heat: String, eventName: String, clock: String?,
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
            eventName, Modifier.weight(1f), color = colors.headerValue, fontFamily = fonts.family,
            maxSize = t.name, minSize = t.nameFloor, textAlign = TextAlign.Center, maxLines = 2,
        )
        if (server != null) {
            Text(server, color = colors.thText, fontSize = t.label, fontFamily = fonts.family, maxLines = 1, softWrap = false)
        }
        if (clock != null) {
            Text(clock, color = colors.headerValue, fontSize = t.value, fontFamily = fonts.digits, maxLines = 1, softWrap = false)
        }
    }
    }
}

/** Material's top app bar height, which the landscape header row is measured against. */
private val BarHeight = 64.dp

/** The word and its number read as one thing, and say nothing at all before a number arrives. */
@Composable
private fun HeaderCell(label: String, value: String, labelSize: TextUnit, valueSize: TextUnit, labelFont: FontFamily, valueFont: FontFamily) {
    val colors = LocalBoardColors.current
    val spokenValue = if (value.isBlank()) "" else "$label $value"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { if (spokenValue.isNotEmpty()) contentDescription = spokenValue },
    ) {
        Text(label, color = colors.headerLabel, fontSize = labelSize, fontFamily = labelFont, maxLines = 1, letterSpacing = 1.sp)
        // The web colours these with `header_label` and sets the seven-segment face; give the tall glyphs their line.
        Text(value.ifEmpty { " " }, color = colors.headerLabel, fontSize = valueSize, lineHeight = valueSize * 1.25f, fontFamily = valueFont, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}

@Composable
private fun InlineCell(label: String, value: String, labelSize: TextUnit, valueSize: TextUnit, labelFont: FontFamily, valueFont: FontFamily) {
    val colors = LocalBoardColors.current
    val spokenValue = if (value.isBlank()) "" else "$label $value"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clearAndSetSemantics { if (spokenValue.isNotEmpty()) contentDescription = spokenValue },
    ) {
        Text(label, color = colors.headerLabel, fontSize = labelSize, fontFamily = labelFont, maxLines = 1, letterSpacing = 1.sp)
        Text(value.ifEmpty { " " }, color = colors.headerLabel, fontSize = valueSize, fontFamily = valueFont, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}
