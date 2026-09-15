package app.splouch.android.ui.shell

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.splouch.android.R
import app.splouch.android.ui.board.BoardBarHeader
import app.splouch.android.ui.results.ResultsTab
import app.splouch.android.ui.schedule.FilterSheet
import app.splouch.android.ui.schedule.ScheduleTab
import app.splouch.android.ui.scoreboard.ScoreboardTab
import app.splouch.android.ui.useNavigationRail
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.session.AppModel
import app.splouch.core.session.MeetState
import app.splouch.core.session.UiState
import kotlinx.coroutines.launch

private const val SCOREBOARD = 0
private const val RESULTS = 1
private const val SCHEDULE = 2

/**
 * The app shell (app.md §2).
 *
 * The shape is the platform's: a Material top app bar carrying `A-02`'s back arrow and
 * the meet's name, three tabs in the navigation bar — or the navigation rail where the
 * window is wide enough for one (`A-07`) — and `A-03`/`A-10`'s pager, which on Android
 * *is* the idiom and so gives both rows at once. Pull-to-refresh is `A-05`, safe areas
 * come free from `Scaffold` (`A-06`), and the selected tab is remembered (`A-04`).
 *
 * In landscape the board's own header (`L-01`–`L-03`) moves into the app bar rather than
 * sitting under it: two bands of chrome for one screen is what the web shell had to do
 * with an `<iframe>`, and the lane table wants the height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetShell(model: AppModel, state: UiState, meet: MeetState) {
    val t = meet.strings
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rail = useNavigationRail()
    val pager = rememberPagerState(initialPage = (state.prefs.tab ?: 0).coerceIn(0, 2)) { 3 }
    val scope = rememberCoroutineScope()

    // S-08/S-20: the filter belongs to the meet, not to the tab — the button that opens it
    // is in the app bar, so the state it drives has to be at least as high up.
    var filter by remember(meet.session) { mutableStateOf(ScheduleFilterState()) }
    var showFilter by remember { mutableStateOf(false) }

    // S-21: a refreshed start list keeps the filters whose names still exist.
    val heats = meet.schedule
    LaunchedEffect(heats) { if (heats != null) filter = ScheduleFilter.retain(filter, heats) }

    BackHandler { model.closeMeet() }

    // L-14 / R-10: the on-appear callbacks the web had to fake with `resize` and `on_tab_shown`.
    LaunchedEffect(pager.settledPage) {
        model.setTab(pager.settledPage)
        when (pager.settledPage) {
            SCOREBOARD -> meet.session.revealScoreboard()
            RESULTS -> meet.session.revealResults()
        }
    }

    val tabs = listOf(
        Triple(t.mobile("scoreboard"), R.drawable.ic_tab_scoreboard, SCOREBOARD),
        Triple(t.mobile("results"), R.drawable.ic_tab_results, RESULTS),
        Triple(t.mobile("schedule"), R.drawable.ic_tab_schedule, SCHEDULE),
    )
    fun go(index: Int) = scope.launch { pager.animateScrollToPage(index) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = { model.closeMeet() }) {
                        Icon(painterResource(R.drawable.ic_back), t.mobile("back_to_meets"))
                    }
                },
                title = {
                    val onBoard = pager.currentPage != SCHEDULE
                    if (landscape && onBoard) {
                        BoardBarHeader(
                            meet,
                            results = pager.currentPage == RESULTS,
                            server = state.server.display.takeIf { !state.isDefaultServer },
                        )
                    } else {
                        Column {
                            Text(
                                meet.config.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            // P-11: a user who switched servers can always see which one this is.
                            if (!state.isDefaultServer) {
                                Text(
                                    state.server.display,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    // S-08: the filter opens from the top bar, and only on the tab it filters.
                    if (pager.currentPage == SCHEDULE) {
                        IconButton(onClick = { showFilter = true }) {
                            // S-12: the count rides the button rather than hanging off a corner.
                            BadgedBox(badge = {
                                if (filter.filters.isNotEmpty()) Badge { Text(filter.filters.size.toString()) }
                            }) {
                                Icon(painterResource(R.drawable.ic_filter), t.mobile("filter"))
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!rail) {
                NavigationBar {
                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = pager.currentPage == index,
                            onClick = { go(index) },
                            icon = { Icon(painterResource(icon), null) },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (rail) {
                NavigationRail {
                    tabs.forEach { (label, icon, index) ->
                        NavigationRailItem(
                            selected = pager.currentPage == index,
                            onClick = { go(index) },
                            icon = { Icon(painterResource(icon), null) },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = meet.refreshing,
                    onRefresh = { model.refreshMeet() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 2) { page ->
                        Box(Modifier.fillMaxSize()) {
                            when (page) {
                                SCOREBOARD -> ScoreboardTab(meet, landscape)
                                RESULTS -> ResultsTab(meet, landscape)
                                else -> ScheduleTab(meet, filter, onResetFilters = { filter = filter.reset() })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        FilterSheet(meet, filter, onChange = { filter = it }, onDismiss = { showFilter = false })
    }
}
