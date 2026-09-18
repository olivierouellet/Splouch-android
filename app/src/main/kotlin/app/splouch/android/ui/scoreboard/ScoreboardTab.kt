package app.splouch.android.ui.scoreboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ui.board.BoardGrid
import app.splouch.android.ui.board.BoardHeader
import app.splouch.android.ui.board.BoardMetrics
import app.splouch.android.ui.board.GridRow
import app.splouch.android.ui.board.boardLabels
import app.splouch.android.ui.board.wallClock
import app.splouch.core.board.LapSettings
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName

/**
 * The live board (app.md §3). Every figure comes off [MeetState.session]; nothing here
 * decides what is on screen.
 *
 * In landscape the header is in the app bar instead (`MeetShell`), so the table gets the
 * whole page rather than sharing it with a second band of chrome.
 */
@Composable
fun ScoreboardTab(meet: MeetState, landscape: Boolean, metrics: BoardMetrics, headerInBar: Boolean) {
    val view by meet.session.scoreboard.collectAsStateWithLifecycle()
    val labels = boardLabels(meet)
    fun label(key: String) = labels[key].orEmpty()
    val vocab = meet.strings.eventVocab
    val eventName = remember(view.eventName, view.eventNameParts, vocab) { EventName.display(view.eventName, view.eventNameParts, vocab) }
    // L-23. The lap is decided in the core, off merged state alone, and the two venue numbers
    // it reads are part of that state — so a phone that joins mid-heat gets the same answer
    // from the cached snapshot as one that watched every frame arrive.
    val laps = remember(meet.config.settings) { LapSettings.from(meet.config.settings) }
    val rows = remember(view.lanes, view.expectedSplits, view.splitStep, laps) {
        view.lanes.map { l ->
            GridRow(
                l.number.toString(), l.pulsing, l.name, l.alt, l.club, l.time, l.timeStyle, l.lockEdge,
                l.deltaSeconds, l.deltaBetter, l.place, view.lap(l, laps),
            )
        }
    }
    Column(Modifier.fillMaxSize()) {
        // The app bar hands this back when it is drawing the row itself — always in
        // landscape, and in portrait only when the lanes need the height (`L-15`).
        if (!headerInBar) {
            BoardHeader(label("event"), view.currentEvent, label("heat"), view.currentHeat, eventName, wallClock(), metrics)
        }
        BoardGrid(rows, meet.config.settings, labels, landscape, metrics, headerInBar, laps = laps)
    }
}
