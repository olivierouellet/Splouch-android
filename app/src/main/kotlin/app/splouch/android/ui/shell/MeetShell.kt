package app.splouch.android.ui.shell

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.splouch.android.R
import app.splouch.android.ui.results.ResultsTab
import app.splouch.android.ui.schedule.ScheduleTab
import app.splouch.android.ui.scoreboard.ScoreboardTab
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.core.session.AppModel
import app.splouch.core.session.MeetState
import app.splouch.core.session.UiState
import kotlinx.coroutines.launch

/**
 * The app shell (app.md §2): three tabs in a pager that follows the finger (A-03), a
 * bottom bar with icon and label (A-01, A-07), back to the picker (A-02), the selected
 * tab remembered (A-04), pull-to-refresh (A-05), safe areas from the platform (A-06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetShell(model: AppModel, state: UiState, meet: MeetState) {
    val colors = LocalBoardColors.current
    val t = meet.strings
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val pager = rememberPagerState(initialPage = (state.prefs.tab ?: 0).coerceIn(0, 2)) { 3 }
    val scope = rememberCoroutineScope()

    BackHandler { model.closeMeet() }

    // L-14 / R-10: the on-appear callbacks the web had to fake with `resize` and `on_tab_shown`.
    LaunchedEffect(pager.settledPage) {
        model.setTab(pager.settledPage)
        when (pager.settledPage) {
            0 -> meet.session.revealScoreboard()
            1 -> meet.session.revealResults()
        }
    }

    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF111111), contentColor = Color(0xFF666666)) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = Color(0xFF2A2A2A),
                    unselectedIconColor = Color(0xFF666666), unselectedTextColor = Color(0xFF666666),
                )
                NavigationBarItem(selected = false, onClick = { model.closeMeet() }, colors = itemColors,
                    icon = { Icon(painterResource(R.drawable.ic_back), t.mobile("back_to_meets")) })
                // Each label is a literal `mobile("…")` call so `SnapshotCoverageTests` can
                // see it: a key reached through a variable would slip past that check.
                listOf(
                    Triple(t.mobile("scoreboard"), R.drawable.ic_tab_scoreboard, 0),
                    Triple(t.mobile("results"), R.drawable.ic_tab_results, 1),
                    Triple(t.mobile("schedule"), R.drawable.ic_tab_schedule, 2),
                ).forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        colors = itemColors,
                        icon = { Icon(painterResource(icon), label) },
                        label = if (landscape) null else ({ Text(label, fontSize = 11.sp, maxLines = 1) }),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // P-11: a user who switched servers can always see which one this is.
            if (!state.isDefaultServer) {
                Text(state.server.display, color = Color(0xFF9A9A9A), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(vertical = 2.dp))
            }
            PullToRefreshBox(isRefreshing = meet.refreshing, onRefresh = { model.refreshMeet() }, modifier = Modifier.fillMaxSize()) {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 2) { page ->
                    Box(Modifier.fillMaxSize()) {
                        when (page) {
                            0 -> ScoreboardTab(meet, landscape)
                            1 -> ResultsTab(meet, landscape)
                            else -> ScheduleTab(meet)
                        }
                    }
                }
            }
        }
    }
}
