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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    val labels = t.labels("short") + meet.labels

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
                    HeatCard(v, labels, t.eventVocab)
                }
            }
        }
    }
}

@Composable
private fun HeatCard(v: VisibleHeat, labels: Map<String, String>, vocab: Map<String, String>) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val h = v.heat
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
            Row(
                Modifier.fillMaxWidth().semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (h.time.isNotEmpty()) {
                    Text(h.time, color = colors.scheduleTime, style = MaterialTheme.typography.titleSmall, fontFamily = fonts.timing)
                }
                Text(
                    "${labels["event"].orEmpty()} ${h.event} — ${labels["heat"].orEmpty()} ${h.heat}",
                    color = colors.scheduleEvent,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = fonts.family, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                val name = EventName.display(h.eventName, h.eventNameParts, vocab)
                if (name.isNotEmpty()) {
                    Text(
                        name, color = colors.rowText, style = MaterialTheme.typography.bodyLarge,
                        fontFamily = fonts.family, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
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
                    if (lane.seedTime.isNotEmpty()) {
                        Text(lane.seedTime, color = colors.scheduleTime, style = MaterialTheme.typography.bodyMedium, fontFamily = fonts.timing, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
