package app.splouch.android.ui.shell

import android.content.res.Configuration
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import app.splouch.android.R
import app.splouch.android.ui.board.BoardBarHeader
import app.splouch.android.ui.board.BoardMetrics
import app.splouch.android.ui.results.ResultsTab
import app.splouch.android.ui.schedule.FilterSheet
import app.splouch.android.ui.schedule.ScheduleTab
import app.splouch.android.ui.scoreboard.ScoreboardTab
import app.splouch.android.ui.useNavigationRail
import app.splouch.core.schedule.ScheduleFilter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.session.AppModel
import app.splouch.core.session.MeetState
import app.splouch.core.session.MeetTab
import app.splouch.core.session.UiState
import kotlinx.coroutines.launch

private fun MeetTab.icon(): Int = when (this) {
    MeetTab.SCOREBOARD -> R.drawable.ic_tab_scoreboard
    MeetTab.RESULTS -> R.drawable.ic_tab_results
    MeetTab.SCHEDULE -> R.drawable.ic_tab_schedule
}

private fun MeetTab.labelKey(): String = when (this) {
    MeetTab.SCOREBOARD -> "scoreboard"
    MeetTab.RESULTS -> "results"
    MeetTab.SCHEDULE -> "schedule"
}

/**
 * The app shell (app.md §2).
 *
 * The shape is the platform's: a Material top app bar carrying `A-02`'s back arrow and
 * the meet's name, the tabs in the navigation bar — or the navigation rail where the
 * window is wide enough for one (`A-07`) — and `A-03`/`A-10`'s pager, which on Android
 * *is* the idiom and so gives both rows at once. Pull-to-refresh is `A-05`, safe areas
 * come free from `Scaffold` (`A-06`), and the selected tab is remembered (`A-04`).
 *
 * **Which tabs, from the config** (`A-11`): [MeetTab.of] is the only place that decides,
 * and it decides on every recomposition, so a config re-fetch that finds the console
 * changed rebuilds the row without an app restart. Both the navigation bar and the pager
 * are built from that one list — two destinations are genuinely two, not three with one
 * hidden, and the pager's page count follows it rather than a constant.
 *
 * In landscape the board's own header (`L-01`–`L-03`) moves into the app bar rather than
 * sitting under it: two bands of chrome for one screen is what the web shell had to do
 * with an `<iframe>`, and the lane table wants the height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetShell(model: AppModel, state: UiState, meet: MeetState, snackbar: SnackbarHostState) {
    val t = meet.strings
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rail = useNavigationRail()
    // A-11: the tab row is the meet's, not the app's. Recomputed rather than remembered,
    // so the config re-fetches of A-05, C-08, reconnect and foreground each land here.
    val tabs = MeetTab.of(meet.config)
    val pager = rememberPagerState(initialPage = tabs.indexOf(state.prefs.tab).coerceAtLeast(0)) { tabs.size }
    val scope = rememberCoroutineScope()
    val tab = tabs.getOrElse(pager.currentPage) { MeetTab.DEFAULT }

    // L-15: what the board has measured about itself, held here because this is the only
    // view that knows both what the lanes want and what the app bar is currently carrying.
    val metrics = remember(meet.session) { BoardMetrics() }

    // S-08/S-20: the filter belongs to the meet, not to the tab — the button that opens it
    // is in the app bar, so the state it drives has to be at least as high up.
    var filter by remember(meet.session) { mutableStateOf(ScheduleFilterState()) }
    var showFilter by remember { mutableStateOf(false) }

    // S-21: a refreshed start list keeps the filters whose names still exist.
    val heats = meet.schedule
    LaunchedEffect(heats) { if (heats != null) filter = ScheduleFilter.retain(filter, heats) }

    // A-02, by the system's own gesture. `PredictiveBackHandler` rather than a plain
    // `BackHandler` so the drag is answered while it happens: the board eases back and
    // fades as the finger pulls, settles if the gesture is completed, and springs back if
    // it is abandoned. Without it (and without the manifest's
    // `enableOnBackInvokedCallback`) Android 13+ gets no preview at all and the meet just
    // vanishes on release.
    val backProgress = remember { mutableFloatStateOf(0f) }
    val close by rememberUpdatedState { model.closeMeet() }
    PredictiveBackHandler { progress ->
        try {
            progress.collect { backProgress.floatValue = it.progress }
            close()
        } catch (cancelled: CancellationException) {
            backProgress.floatValue = 0f
            throw cancelled
        } finally {
            backProgress.floatValue = 0f
        }
    }

    // L-14 / R-10: the on-appear callbacks the web had to fake with `resize` and `on_tab_shown`.
    //
    // Keyed on the settled page alone, deliberately — **not** on [tabs]. A page number is
    // only a choice while the row it indexes is the row the reader saw: drop Results and
    // page 1 stops meaning Results and starts meaning Schedule without the reader moving a
    // finger. So this fires on movement, the effect below fires on the row changing, and
    // neither has to guess which of the two just happened.
    LaunchedEffect(pager.settledPage) {
        val settled = tabs.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        model.setTab(settled)
        when (settled) {
            MeetTab.SCOREBOARD -> meet.session.revealScoreboard()
            MeetTab.RESULTS -> meet.session.revealResults()
            MeetTab.SCHEDULE -> Unit
        }
    }

    // A-11, the live half: the operator can switch consoles mid-meet in either direction,
    // and the Pi re-registers and broadcasts `reload` when they do, so the row changes
    // under a spectator standing on it. The stored choice is an identity (`A-04`), so
    // Schedule stays Schedule whether it is page 1 of two or page 2 of three, and only a
    // reader whose own tab has just ceased to exist is moved — to the Scoreboard, which is
    // what the operator is driving. The move re-settles the pager, so the effect above
    // records where they ended up.
    //
    // `scrollToPage`, not `animateScrollToPage`: this is not a navigation the reader asked
    // for, and sliding them across the pager would read as one.
    LaunchedEffect(tabs) {
        val want = tabs.indexOf(state.prefs.tab)
        val target = if (want >= 0) want else tabs.indexOf(MeetTab.DEFAULT).coerceAtLeast(0)
        if (target != pager.currentPage) pager.scrollToPage(target)
    }

    fun go(index: Int) = scope.launch { pager.animateScrollToPage(index) }

    // Pinned, not collapsing: where this bar *is* the board header (L-01..L-03), a board
    // that hides the heat number to win back a row is not a board.
    val barScroll = TopAppBarDefaults.pinnedScrollBehavior()

    /**
     * L-15, the top rung of the ladder: the board's own EVENT / HEAT row moves into the app
     * bar, which is worth a whole header band. Landscape always does it — two strips of
     * chrome for one screen is what the web shell had to do with an `<iframe>`, and the lane
     * table wants the height. **Portrait does it only on need**: the bar there is carrying
     * the meet's title and P-11's server name, so the trade is a real one and not worth
     * making for a board that fits without it. Ten lanes and under keep them.
     *
     * The need is `BoardMetrics.wantsBar`, which is normalised so that moving the row cannot
     * hand back the space that caused the move — see there.
     */
    val onBoard = tab != MeetTab.SCHEDULE
    val headerInBar = onBoard && (landscape || metrics.needsBar)

    Scaffold(
        modifier = Modifier
            .scale(1f - 0.08f * backProgress.floatValue)
            .alpha(1f - 0.25f * backProgress.floatValue)
            .nestedScroll(barScroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = barScroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = { model.closeMeet() }) {
                        Icon(painterResource(R.drawable.ic_back), t.mobile("back_to_meets"))
                    }
                },
                title = {
                    if (headerInBar) {
                        BoardBarHeader(
                            meet,
                            results = tab == MeetTab.RESULTS,
                            landscape = landscape,
                            // In portrait the bar has given up the meet's title to make room,
                            // so P-11's server name has nowhere else to go either.
                            server = state.server.display.takeIf { !state.isDefaultServer && landscape },
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
                    if (tab == MeetTab.SCHEDULE) {
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
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = pager.currentPage == index,
                            onClick = { go(index) },
                            icon = { Icon(painterResource(item.icon()), null) },
                            label = { Text(t.mobile(item.labelKey()), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (rail) {
                NavigationRail {
                    tabs.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = pager.currentPage == index,
                            onClick = { go(index) },
                            icon = { Icon(painterResource(item.icon()), null) },
                            label = { Text(t.mobile(item.labelKey()), maxLines = 1) },
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
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = tabs.size - 1) { page ->
                        Box(Modifier.fillMaxSize()) {
                            when (tabs.getOrElse(page) { MeetTab.DEFAULT }) {
                                // Both board tabs answer the same question, the way they
                                // already did in landscape: the bar carries whichever one is
                                // in view, so neither draws its own band.
                                MeetTab.SCOREBOARD -> ScoreboardTab(meet, landscape, metrics, headerInBar)
                                MeetTab.RESULTS -> ResultsTab(meet, landscape, metrics, headerInBar)
                                MeetTab.SCHEDULE -> ScheduleTab(meet, filter, onResetFilters = { filter = filter.reset() })
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
