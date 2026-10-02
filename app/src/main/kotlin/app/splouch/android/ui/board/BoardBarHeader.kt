package app.splouch.android.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.core.session.MeetState
import app.splouch.core.strings.EventName
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The board's header where the app bar carries it (landscape, and portrait on need). The
 * shell cannot ask the tab for this — a pager page that is off screen is still composed — so
 * it reads the same two flows the tabs do and shows whichever belongs to the page in view.
 */
@Composable
fun BoardBarHeader(meet: MeetState, results: Boolean, landscape: Boolean, server: String? = null) {
    // The bar is one line high, so the word sits *beside* its number here, in either
    // orientation — and inline takes the short word, as on the web board. "EVENT 12 HEAT 3"
    // buys nothing over "EV 12  HT 3" while the width it costs is the event name's, the one
    // thing on this row that can run long. The long word is `BoardHeader`'s, where it stands
    // over its number with the cell's width to itself.
    val labels = inlineLabels(meet)
    fun label(key: String) = labels[key].orEmpty()
    val vocab = meet.strings.eventVocab
    // Landscape keeps the wall clock; portrait does not. The row is only up here in portrait
    // because the board ran out of height, and the clock is the one thing on it that is not
    // about this heat — the status bar is showing the time a few points above it (`L-03`).
    val clock = if (landscape) wallClock() else null
    if (results) {
        val view by meet.session.resultsView.collectAsStateWithLifecycle()
        val name =
            remember(view.eventName, view.eventNameParts, vocab) {
                EventName.display(view.eventName, view.eventNameParts, vocab)
            }
        BoardBarHeaderRow(label("event"), view.event, label("heat"), view.heat, name, clock, server)
    } else {
        val view by meet.session.scoreboard.collectAsStateWithLifecycle()
        val name =
            remember(view.eventName, view.eventNameParts, vocab) {
                EventName.display(view.eventName, view.eventNameParts, vocab)
            }
        BoardBarHeaderRow(label("event"), view.currentEvent, label("heat"), view.currentHeat, name, clock, server)
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

/**
 * The words for EVENT / HEAT where they sit inline beside their number: the short forms,
 * still the server's (`T-04`) — `MeetState.shortLabels` is the operator's own words with
 * only the two wide keys taken short — over the language table's so nothing is unlabelled.
 */
@Composable
fun inlineLabels(meet: MeetState): Map<String, String> {
    val short = meet.strings.labels("short")
    return remember(short, meet.shortLabels) { short + meet.shortLabels }
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
