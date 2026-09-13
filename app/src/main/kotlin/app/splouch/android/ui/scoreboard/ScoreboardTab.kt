package app.splouch.android.ui.scoreboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ui.board.BoardGrid
import app.splouch.android.ui.board.BoardHeader
import app.splouch.android.ui.board.GridRow
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The live board (app.md §3). Every figure comes off [MeetState.session]; nothing here decides what is on screen. */
@Composable
fun ScoreboardTab(meet: MeetState, landscape: Boolean) {
    val view by meet.session.scoreboard.collectAsStateWithLifecycle()
    val labels = meet.labels
    val short = meet.strings.labels("short")
    fun label(key: String) = labels[key] ?: short[key] ?: ""
    val vocab = meet.strings.eventVocab
    val eventName = remember(view.eventName, view.eventNameParts, vocab) { EventName.display(view.eventName, view.eventNameParts, vocab) }
    val rows = remember(view.lanes) {
        view.lanes.map { l ->
            GridRow(l.number.toString(), l.pulsing, l.name, l.alt, l.club, l.time, l.timeStyle, l.lockEdge, l.deltaSeconds, l.deltaBetter, l.place)
        }
    }
    Column(Modifier.fillMaxSize()) {
        BoardHeader(label("event"), view.currentEvent, label("heat"), view.currentHeat, eventName, landscape, wallClock())
        BoardGrid(rows, meet.config.settings, labels.withFallback(short), landscape)
    }
}

/** L-03: device local time, `HH:mm`, ticking every second. */
@Composable
fun wallClock(): String {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val text by produceState(LocalTime.now().format(fmt)) {
        while (true) {
            value = LocalTime.now().format(fmt)
            delay(1000 - (System.currentTimeMillis() % 1000))
        }
    }
    return text
}

internal fun Map<String, String>.withFallback(fallback: Map<String, String>): Map<String, String> = fallback + this
