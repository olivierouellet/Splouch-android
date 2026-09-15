package app.splouch.android.ui.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ui.board.BoardGrid
import app.splouch.android.ui.board.BoardHeader
import app.splouch.android.ui.board.GridRow
import app.splouch.android.ui.board.boardLabels
import app.splouch.android.ui.board.wallClock
import app.splouch.android.ui.common.EmptyState
import app.splouch.core.board.ScoreboardState.TimeStyle
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName

/** The last confirmed heat, held still (app.md §4). */
@Composable
fun ResultsTab(meet: MeetState, landscape: Boolean) {
    val view by meet.session.resultsView.collectAsStateWithLifecycle()
    val labels = boardLabels(meet)
    fun label(key: String) = labels[key].orEmpty()
    val vocab = meet.strings.eventVocab
    val eventName = remember(view.eventName, view.eventNameParts, vocab) { EventName.display(view.eventName, view.eventNameParts, vocab) }
    val rows = remember(view.rows) {
        view.rows.map { r ->
            GridRow(r.laneLabel, false, r.name, r.alt, r.club, r.time, if (r.locked) TimeStyle.LOCKED else TimeStyle.NORMAL, 0, r.deltaSeconds, r.deltaBetter, r.place)
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (!landscape) {
            BoardHeader(label("event"), view.event, label("heat"), view.heat, eventName, wallClock())
        }
        if (view.waiting) {
            // R-01: the line is the screen, and the table appears with the data. Blank rows
            // mean something on the Scoreboard, where a heat is live and they fill in
            // (L-09); before the first snapshot they are a table the web drew to occupy the
            // page, and a spectator reads nothing from it the line does not already say.
            EmptyState(title = meet.strings.mobile("waiting_results"))
        } else {
            BoardGrid(rows, meet.config.settings, labels, landscape)
        }
    }
}
