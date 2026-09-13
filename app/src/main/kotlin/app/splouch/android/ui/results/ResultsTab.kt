package app.splouch.android.ui.results

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ui.board.BoardGrid
import app.splouch.android.ui.board.BoardHeader
import app.splouch.android.ui.board.GridRow
import app.splouch.android.ui.scoreboard.wallClock
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.android.ui.theme.LocalBoardFonts
import app.splouch.core.board.ScoreboardState.TimeStyle
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName

/** The last confirmed heat, held still (app.md §4). */
@Composable
fun ResultsTab(meet: MeetState, landscape: Boolean) {
    val view by meet.session.resultsView.collectAsStateWithLifecycle()
    val labels = meet.labels
    val short = meet.strings.labels("short")
    fun label(key: String) = labels[key] ?: short[key] ?: ""
    val vocab = meet.strings.eventVocab
    val eventName = remember(view.eventName, view.eventNameParts, vocab) { EventName.display(view.eventName, view.eventNameParts, vocab) }
    val rows = remember(view.rows) {
        view.rows.map { r ->
            GridRow(r.laneLabel, false, r.name, r.alt, r.club, r.time, if (r.locked) TimeStyle.LOCKED else TimeStyle.NORMAL, 0, r.deltaSeconds, r.deltaBetter, r.place)
        }
    }
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize()) {
        BoardHeader(label("event"), view.event, label("heat"), view.heat, eventName, landscape, wallClock())
        BoardGrid(rows, meet.config.settings, short + labels, landscape, footer = if (view.waiting && !landscape) ({
            // R-01: under the empty grid, wherever there is room to say so; landscape has none.
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text(meet.strings.mobile("waiting_results"), color = colors.thText, fontSize = 20.sp, fontFamily = LocalBoardFonts.current.family, textAlign = TextAlign.Center)
            }
        }) else null)
    }
}
