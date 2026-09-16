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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
) {
    if (landscape) LandscapeGrid(rows, settings, labels, modifier) else PortraitGrid(rows, settings, labels, modifier)
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
private fun PortraitGrid(rows: List<GridRow>, settings: MeetSettings, labels: Map<String, String>, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val cfg = LocalConfiguration.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The lanes share the board the way the landscape table already did: a six-lane
        // meet fills the screen instead of drawing 300dp of stripes above bare background.
        // The floor is what a two-line row needs; past it the board scrolls.
        val shared = if (rows.isEmpty()) RowFloor else maxHeight / rows.size
        val rowHeight = if (shared > RowFloor) shared else RowFloor
        // And the type follows the height the rows actually got, so a six-lane meet is
        // read across the pool rather than set at the size a sixteen-lane one needs.
        //
        // Through `toSp()`, the way the header already does it: the size is a fraction of
        // a height in `dp`, and declaring that fraction in `sp` let the device's font-size
        // setting multiply it a second time inside a row that had not grown at all. The
        // name cell hid it — `L-17` shrinks to fit — but the club, time, delta and place
        // have no such give, and by 2× they were running out of the row. Scaling happens
        // once and it is the caller's: the board sizes itself from the height it has
        // (`L-15`, `L-16`), the schedule and the chrome follow the setting. See `parity.md` §8.
        val base = with(LocalDensity.current) { (rowHeight * 0.26f).coerceIn(13.dp, 24.dp).toSp() }
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            rows.forEachIndexed { i, r ->
                val description = spoken(r, settings, labels)
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .background(if (i % 2 == 0) colors.rowOdd else colors.rowEven)
                        .padding(vertical = 2.dp)
                        .clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LaneNumber(r.lane, r.pulsing, base, Modifier.width((cfg.screenWidthDp * 0.09f).dp))
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (settings.showName) AutoSizeText(r.name, Modifier.weight(1f), maxSize = base) else Box(Modifier.weight(1f))
                            if (settings.showClub) Text(r.club, color = colors.thText, fontSize = base * 0.75f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontFamily = LocalBoardFonts.current.family, textAlign = TextAlign.End, modifier = Modifier.padding(start = 6.dp))
                        }
                        if (settings.showName && r.alt.isNotEmpty()) {
                            // L-06: relay members, dimmed, under the name.
                            Text(r.alt, color = colors.thText, fontSize = base * 0.62f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = LocalBoardFonts.current.family)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeText(r.time, r.timeStyle, r.lockEdge, base * 0.92f, Modifier.weight(1f), TextAlign.Start)
                            if (settings.showDelta) DeltaText(r.deltaSeconds, r.deltaBetter, base * 0.78f)
                            if (settings.showPosition) PlaceText(r.place, base * 0.78f, Modifier.width((cfg.screenWidthDp * 0.12f).dp))
                        }
                    }
                }
            }
        }
    }
}

// ── landscape: the full table, font scaled to lane count (L-16) ──────────────

@Composable
private fun LandscapeGrid(rows: List<GridRow>, settings: MeetSettings, labels: Map<String, String>, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val anyHeader = settings.showLaneHeader || (settings.showName && settings.showNameHeader) || (settings.showClub && settings.showClubHeader) ||
        settings.showTimeHeader || (settings.showDelta && settings.showDeltaHeader) || (settings.showPosition && settings.showPositionHeader)
    Column(modifier.fillMaxSize()) {
        if (anyHeader) {
            Row(Modifier.fillMaxWidth().background(colors.thBg).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                // Fixed at 13dp rather than 13sp: the titles sit above a board that sizes
                // itself from the height it has, and a header that grew with the font-size
                // setting would take that height from the lanes underneath it.
                val thSize = with(LocalDensity.current) { 13.dp.toSp() }
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowHeight: Dp = maxHeight / rows.size.coerceAtLeast(1)
            // `toSp()` for the same reason the portrait rows use it: the share is a height,
            // and the font-size setting must not multiply it a second time. See `parity.md` §8.
            val size = with(LocalDensity.current) { (rowHeight * 0.48f).coerceIn(10.dp, 32.dp).toSp() }
            Column(Modifier.fillMaxSize()) {
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
                                AutoSizeText(r.name, Modifier.fillMaxWidth(), maxSize = size * 0.85f)
                                if (r.alt.isNotEmpty()) Text(r.alt, color = colors.thText, fontSize = size * 0.5f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fonts.family)
                            }
                        }
                        if (settings.showClub) Text(r.club, color = colors.rowText, fontSize = size * 0.68f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = fonts.family,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f).padding(horizontal = 6.dp))
                        TimeText(r.time, r.timeStyle, r.lockEdge, size * 0.72f, Modifier.width(TimeW), TextAlign.Center)
                        if (settings.showDelta) Box(Modifier.width(DeltaW), contentAlignment = Alignment.Center) { DeltaText(r.deltaSeconds, r.deltaBetter, size * 0.58f) }
                        if (settings.showPosition) PlaceText(r.place, size * 0.7f, Modifier.width(PlaceW), Arrangement.Center)
                    }
                }
            }
        }
    }
}

/** What a two-line compact row needs before it has to scroll instead of share. */
private val RowFloor = 44.dp

private val LaneW = 52.dp
private val TimeW = 130.dp
private val DeltaW = 96.dp
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

@Composable
private fun DeltaText(seconds: Double?, better: Boolean?, size: TextUnit) {
    val colors = LocalBoardColors.current
    val color = when (better) { true -> colors.deltaBetter; false -> colors.deltaWorse; null -> colors.rowText }
    Text(DeltaFormat.text(seconds), color = color, fontSize = size, fontFamily = LocalBoardFonts.current.timing, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
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
fun BoardHeader(eventLabel: String, event: String, heatLabel: String, heat: String, eventName: String, clock: String?) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val cfg = LocalConfiguration.current
    val height = (cfg.screenHeightDp * 0.085f).coerceIn(52f, 92f).dp
    val t = headerType(height)
    Row(
        Modifier.fillMaxWidth().heightIn(min = height).padding(horizontal = 12.dp, vertical = 8.dp),
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
