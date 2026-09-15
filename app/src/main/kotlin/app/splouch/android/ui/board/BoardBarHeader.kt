package app.splouch.android.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The board's header where the app bar carries it (landscape). The shell cannot ask the
 * tab for this — a pager page that is off screen is still composed — so it reads the same
 * two flows the tabs do and shows whichever belongs to the page in view.
 */
@Composable
fun BoardBarHeader(meet: MeetState, results: Boolean, server: String? = null) {
    val labels = boardLabels(meet)
    fun label(key: String) = labels[key].orEmpty()
    val vocab = meet.strings.eventVocab
    if (results) {
        val view by meet.session.resultsView.collectAsStateWithLifecycle()
        val name = remember(view.eventName, view.eventNameParts, vocab) { EventName.display(view.eventName, view.eventNameParts, vocab) }
        BoardBarHeaderRow(label("event"), view.event, label("heat"), view.heat, name, wallClock(), server)
    } else {
        val view by meet.session.scoreboard.collectAsStateWithLifecycle()
        val name = remember(view.eventName, view.eventNameParts, vocab) { EventName.display(view.eventName, view.eventNameParts, vocab) }
        BoardBarHeaderRow(label("event"), view.currentEvent, label("heat"), view.currentHeat, name, wallClock(), server)
    }
}

/**
 * T-04: the operator's words as sent, with the language table's short forms underneath so
 * a column the meet left unlabelled still has one.
 */
@Composable
fun boardLabels(meet: MeetState): Map<String, String> {
    val short = meet.strings.labels("short")
    return remember(short, meet.labels) { short + meet.labels }
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
