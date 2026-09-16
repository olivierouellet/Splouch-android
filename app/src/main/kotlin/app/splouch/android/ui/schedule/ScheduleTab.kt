package app.splouch.android.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.R
import app.splouch.android.ui.common.EmptyState
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.android.ui.theme.LocalBoardFonts
import app.splouch.core.schedule.EmptyState as ScheduleEmptyState
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.schedule.VisibleHeat
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName

/**
 * The start list, filterable (app.md §5). Filters live only for the session (S-20) and
 * belong to the meet, so they are held by the shell — the button that opens the sheet is
 * in the app bar (S-08), which is above this tab.
 *
 * Type sizes are the platform's body scale rather than the web stylesheet's: a swimmer's
 * name is the thing a spectator reads hardest on this screen, and at the web's 14px it
 * was four points under every other app's body text.
 */
@Composable
fun ScheduleTab(meet: MeetState, filter: ScheduleFilterState, onResetFilters: () -> Unit) {
    val t = meet.strings
    val current by meet.session.currentHeat.collectAsStateWithLifecycle()
    val heats = meet.schedule
    val visible = remember(heats, filter, current) { if (heats == null) emptyList() else ScheduleFilter.visible(heats, filter, current) }
    val empty = remember(heats, visible, filter) { if (heats == null) ScheduleEmptyState.NONE else ScheduleFilter.emptyState(heats, visible, filter) }
    // The short pair (`EV`/`HT`), not the board's long words: it repeats once per card and
    // the long form costs the event name its width. The board's own column headers keep
    // the long forms (`T-04`).
    val labels = t.labels("short") + meet.shortLabels
    // One seed column for the whole screen, not one per card — see `timingColumn`.
    val seedTemplate = remember(visible) { ScheduleFilter.widestSeedTime(visible) }

    // S-06: scroll to the current heat once per appearance, re-armed on returning to the foreground.
    val listState = rememberLazyListState()
    var needsScroll by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) { needsScroll = true; onPauseOrDispose { } }
    LaunchedEffect(visible, needsScroll) {
        if (!needsScroll) return@LaunchedEffect
        val idx = visible.indexOfFirst { it.isCurrent }
        if (idx >= 0) { listState.animateScrollToItem(idx); needsScroll = false }
    }

    Box(Modifier.fillMaxSize().background(LocalBoardColors.current.bg)) {
        when {
            // A start list that would not load is a network fault, not an empty meet;
            // A-05's pull-to-refresh is the way back, and it works on an empty tab.
            heats == null && meet.scheduleError ->
                EmptyState(icon = painterResource(R.drawable.ic_cloud_off), title = stringResource(R.string.server_unreachable))
            heats == null -> Unit
            // S-07: loaded, no schedule yet.
            empty == ScheduleEmptyState.NO_SCHEDULE ->
                EmptyState(icon = painterResource(R.drawable.ic_no_events), title = t.mobile("no_schedule"))
            // S-19: distinct from the filter sheet's "no search results".
            empty == ScheduleEmptyState.NO_MATCHES -> EmptyState(
                icon = painterResource(R.drawable.ic_search_off),
                title = t.mobile("no_matches"),
                actionLabel = t.mobile("reset_filters"),
                onAction = onResetFilters,
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.heat.event + "/" + it.heat.heat }) { v ->
                    HeatCard(v, labels, t.eventVocab, seedTemplate)
                }
            }
        }
    }
}

@Composable
private fun HeatCard(v: VisibleHeat, labels: Map<String, String>, vocab: Map<String, String>, seedTemplate: String) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val h = v.heat
    // Past this the row reflows instead of shrinking: the heading takes the full width so
    // it breaks at a space rather than down a narrow gutter, and the scheduled time drops
    // to a line of its own — still trailing, still in the timing column.
    val roomy = LocalConfiguration.current.fontScale < 1.8f
    // S-04: the stripe is computed over visible cards.
    val bg = if (v.index % 2 == 0) colors.rowEven else colors.rowOdd
    // IntrinsicSize.Min so the accent bar below has a height to fill: inside a lazy list
    // the row's height constraint is unbounded, and `fillMaxHeight` against that is zero.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(bg)) {
        // S-05: the heat the meet is on, marked by a bar down its leading edge rather than
        // a box drawn around it — the list scrolls to this card, so it has to read at a
        // glance without redrawing the card's own edges.
        Box(
            Modifier.width(4.dp).fillMaxHeight()
                .background(if (v.isCurrent) colors.time else Color.Transparent),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Two spaces where an em dash was. The dash was punctuation between two things
            // that are not a range or a pair — it cost four characters of the event name
            // beside it and said nothing the gap does not. Twice the within-pair gap is
            // what groups "EV 12" against "HT 3" in a monospaced face, so the reading is
            // the same and the line is shorter.
            val eventHeat = "${labels["event"].orEmpty()} ${h.event}  ${labels["heat"].orEmpty()} ${h.heat}"
            val name = EventName.display(h.eventName, h.eventNameParts, vocab)
            val heading: @Composable (Int) -> Unit = { lines ->
                Text(
                    eventHeat, color = colors.scheduleEvent,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = fonts.family, fontWeight = FontWeight.SemiBold, maxLines = lines,
                )
            }
            val eventName: @Composable (Modifier, Int) -> Unit = { m, lines ->
                if (name.isNotEmpty()) {
                    Text(
                        name, color = colors.rowText, style = MaterialTheme.typography.bodyLarge,
                        fontFamily = fonts.family, maxLines = lines, overflow = TextOverflow.Ellipsis,
                        modifier = m,
                    )
                }
            }
            Column(Modifier.fillMaxWidth().semantics { heading() }) {
                if (roomy) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        heading(1)
                        eventName(Modifier.weight(1f), 1)
                        // The scheduled time moved from the front of this row to the
                        // trailing column the seed times sit in, so a card reads as two
                        // columns rather than three loose runs of text: what the heat is on
                        // the left, when it swims on the right, level with every time below.
                        TimingCell(h.time, MaterialTheme.typography.titleSmall, seedTemplate)
                    }
                } else {
                    // Every line gets the full width. The scheduled time used to sit beside
                    // the heat identifier here too, and at these sizes it took a third of
                    // the card and left "EV 12" / "HT 3" to wrap down a narrow gutter.
                    heading(Int.MAX_VALUE)
                    eventName(Modifier.fillMaxWidth(), Int.MAX_VALUE)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TimingCell(h.time, MaterialTheme.typography.titleSmall, seedTemplate)
                    }
                }
            }
            v.lanes.forEach { lane ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        lane.lane?.toString() ?: "", color = colors.thText,
                        style = MaterialTheme.typography.bodyMedium, fontFamily = fonts.family,
                        textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 24.dp),
                    )
                    Text(
                        ScheduleFilter.displayName(lane), color = colors.scheduleName,
                        style = MaterialTheme.typography.bodyLarge, fontFamily = fonts.family,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (lane.club.isNotEmpty()) {
                        Text(lane.club, color = colors.scheduleClub, style = MaterialTheme.typography.bodyMedium, fontFamily = fonts.family, maxLines = 1)
                    }
                    TimingCell(lane.seedTime, MaterialTheme.typography.bodyMedium, seedTemplate)
                }
            }
        }
    }
}

/**
 * The timing column: as wide as the widest seed time on screen, with its value at the
 * trailing edge. Both a lane's seed time and a heading's scheduled time sit in it, so
 * every time on a card shares one right edge.
 *
 * The club and the time used to be packed against the right edge at their natural widths,
 * so the club's position followed the width of the time beside it and the codes zig-zagged
 * down the card. A lane with no time reads "NT", six characters narrower than "1:04.219",
 * which threw its club that much further out; but "57.40" against "1:04.219" was already
 * enough to break the column on any ordinary heat.
 *
 * Sized from a hidden copy of the longest string rather than a constant, so a meet whose
 * every seed time is "NT" reserves two characters and not eight. The ruler is always at
 * the lane's own size, so a heading's `titleSmall` and a lane's `bodyMedium` still share
 * one column. It is drawn transparent rather than skipped — it has to measure — and taken
 * out of the accessibility tree, because TalkBack reading every row's column width before
 * its time would be worse than the misalignment it fixes.
 *
 * Never wrapped: a seed time broken across two lines reads as two times.
 */
@Composable
private fun TimingCell(value: String, style: TextStyle, seedTemplate: String) {
    // Nothing on screen carries a time, so there is no column to keep.
    if (value.isEmpty() && seedTemplate.isEmpty()) return
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val text = @Composable {
        Text(value, color = colors.scheduleTime, style = style, fontFamily = fonts.timing, maxLines = 1, softWrap = false)
    }
    if (seedTemplate.isEmpty()) {
        // A heading's time is then the only one here, and can sit at its own width.
        text()
    } else {
        Box(contentAlignment = Alignment.CenterEnd) {
            Text(
                seedTemplate, style = MaterialTheme.typography.bodyMedium, fontFamily = fonts.timing,
                maxLines = 1, softWrap = false,
                modifier = Modifier.alpha(0f).clearAndSetSemantics { },
            )
            text()
        }
    }
}
