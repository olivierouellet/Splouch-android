package app.splouch.android.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.R
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.android.ui.theme.LocalBoardFonts
import app.splouch.android.ui.ui
import app.splouch.core.schedule.EmptyState
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.schedule.VisibleHeat
import app.splouch.core.session.AppModel
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName

/** The start list, filterable (app.md §5). Filters live only for the session (S-20). */
@Composable
fun ScheduleTab(model: AppModel, meet: MeetState) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val t = meet.strings
    val current by meet.session.currentHeat.collectAsStateWithLifecycle()
    var filter by remember(meet.session) { mutableStateOf(ScheduleFilterState()) }
    var showFilter by remember { mutableStateOf(false) }
    val heats = meet.schedule
    // S-21: a refreshed list keeps the filters whose names still exist.
    LaunchedEffect(heats) { if (heats != null) filter = ScheduleFilter.retain(filter, heats) }
    val visible = remember(heats, filter, current) { if (heats == null) emptyList() else ScheduleFilter.visible(heats, filter, current) }
    val empty = remember(heats, visible, filter) { if (heats == null) EmptyState.NONE else ScheduleFilter.emptyState(heats, visible, filter) }
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

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(colors.headerBg).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(meet.config.title.uppercase(), color = colors.headerLabel, fontSize = 13.sp, letterSpacing = 1.sp, fontFamily = fonts.family, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showFilter = true }, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(painterResource(R.drawable.ic_filter), null, tint = colors.headerValue)
                Text(t.ui("filter"), color = colors.headerValue, fontSize = 13.sp, fontFamily = fonts.family, modifier = Modifier.padding(start = 6.dp))
                // S-12: how many filters are active.
                if (filter.filters.isNotEmpty()) {
                    Text(filter.filters.size.toString(), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 6.dp).background(colors.scheduleEvent, RoundedCornerShape(10.dp)).widthIn(min = 18.dp).padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
        }
        HorizontalDivider(color = colors.headerBorder)
        when {
            heats == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (meet.scheduleError) t.ui("retry") else "", color = colors.thText)
            }
            empty == EmptyState.NO_SCHEDULE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // S-07: loaded, no schedule yet.
                Text(t.mobile("no_schedule"), color = colors.thText, fontSize = 20.sp, fontFamily = fonts.family, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
            empty == EmptyState.NO_MATCHES -> Box(Modifier.fillMaxWidth().padding(48.dp, 48.dp), contentAlignment = Alignment.Center) {
                Text(t.ui("no_matches"), color = colors.scheduleClub, fontSize = 15.sp, fontFamily = fonts.family, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(visible, key = { _, v -> v.heat.event + "/" + v.heat.heat }) { _, v ->
                    HeatCard(v, labels, t.eventVocab)
                }
            }
        }
    }
    if (showFilter) FilterSheet(model, meet, filter, onChange = { filter = it }, onDismiss = { showFilter = false })
}

@Composable
private fun HeatCard(v: VisibleHeat, labels: Map<String, String>, vocab: Map<String, String>) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val h = v.heat
    // S-04: the stripe is computed over visible cards.
    val bg = if (v.index % 2 == 0) colors.rowEven else colors.rowOdd
    Column(
        Modifier.fillMaxWidth().background(bg)
            .then(if (v.isCurrent) Modifier.border(2.dp, colors.time) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (v.isCurrent) colors.headerBorder else Color.Transparent).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (h.time.isNotEmpty()) Text(h.time, color = colors.scheduleTime, fontSize = 15.sp, fontFamily = fonts.timing)
            Text("${labels["event"].orEmpty()} ${h.event} — ${labels["heat"].orEmpty()} ${h.heat}".uppercase(), color = colors.scheduleEvent, fontSize = 12.sp, letterSpacing = 0.8.sp, fontFamily = fonts.family, maxLines = 1)
            val name = EventName.display(h.eventName, h.eventNameParts, vocab)
            if (name.isNotEmpty()) Text(name, color = colors.headerValue, fontSize = 14.sp, fontFamily = fonts.family, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = colors.headerBorder)
        v.lanes.forEachIndexed { i, lane ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lane.lane?.toString() ?: "", color = colors.thText, fontSize = 12.sp, fontFamily = fonts.family, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 22.dp))
                Text(ScheduleFilter.displayName(lane), color = colors.scheduleName, fontSize = 14.sp, fontFamily = fonts.family, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (lane.club.isNotEmpty()) Text(lane.club, color = colors.scheduleClub, fontSize = 12.sp, fontFamily = fonts.family, maxLines = 1)
                if (lane.seedTime.isNotEmpty()) Text(lane.seedTime, color = colors.scheduleTime, fontSize = 12.sp, fontFamily = fonts.timing, maxLines = 1)
            }
            if (i < v.lanes.lastIndex) HorizontalDivider(color = colors.headerBorder)
        }
        HorizontalDivider(color = colors.headerBorder)
    }
}
