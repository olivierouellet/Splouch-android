package app.splouch.android.ui.intro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.splouch.android.R
import app.splouch.android.ui.board.LaneKey
import app.splouch.android.ui.board.LaneKeyWords
import app.splouch.android.ui.settings.CountingSwitch
import app.splouch.android.ui.theme.BoardTheme
import app.splouch.core.session.AppModel
import app.splouch.core.session.UiState
import app.splouch.core.theme.Theme
import kotlinx.coroutines.launch

/** P-20's pages, in order. [COUNTING] only while the server counts. */
private enum class IntroPage { RESULTS, MEETS, TABS, TIMES, FOLLOW, COUNTING }

/**
 * P-20: up to six short pages — icon, title, a sentence per line — paged with dots,
 * skippable from the first, ending on the picker. Shown by the root only once
 * `GET /picker/config` has answered, so the first and last pages carry the server's words; the rest are the app's, with the
 * server's own names for the tabs, the filter and *All heats* quoted inside them so the
 * page and the screen it describes agree.
 *
 * **Not consent.** Skip and Done both only mark it seen; counting stays as it was, and the
 * switch on the last page is the same setting as Settings › Privacy.
 */
@Composable
fun IntroScreen(model: AppModel, state: UiState) {
    val t = state.pickerStrings
    val pages = buildList {
        addAll(listOf(IntroPage.RESULTS, IntroPage.MEETS, IntroPage.TABS, IntroPage.TIMES, IntroPage.FOLLOW))
        if (state.countingOffered) add(IntroPage.COUNTING)
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage >= pages.lastIndex
    fun goTo(page: Int) = scope.launch { pager.animateScrollToPage(page) }

    // Back pages back, and from the first page it is a skip.
    BackHandler { if (pager.currentPage > 0) goTo(pager.currentPage - 1) else model.finishIntro() }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!last) TextButton(onClick = model::finishIntro) { Text(stringResource(R.string.intro_skip)) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                when (pages[i]) {
                    IntroPage.RESULTS -> Page(
                        R.drawable.ic_hourglass_top,
                        stringResource(R.string.intro_results_title),
                        state.disclaimer?.full.orEmpty(),
                    )
                    IntroPage.MEETS -> Page(
                        R.drawable.ic_search,
                        stringResource(R.string.intro_meets_title),
                        stringResource(R.string.intro_meets_body),
                    )
                    IntroPage.TABS -> Page(
                        R.drawable.ic_tab_scoreboard,
                        stringResource(R.string.intro_tabs_title),
                        stringResource(
                            R.string.intro_tabs_body,
                            t.mobile("scoreboard"),
                            t.mobile("results"),
                            t.mobile("schedule"),
                        ),
                    ) {
                        // P-20: how to read a lane, in the server's default palette for the
                        // reader's Appearance (`P-15`) — the board's own, there being no meet yet.
                        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        BoardTheme(dark, Theme.DEFAULT) {
                            LaneKey(
                                LaneKeyWords(
                                    stringResource(R.string.intro_key_lane),
                                    stringResource(R.string.intro_key_club),
                                    stringResource(R.string.intro_key_time),
                                    stringResource(R.string.intro_key_gap),
                                    stringResource(R.string.intro_key_place),
                                ),
                                Modifier.clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                    IntroPage.TIMES -> Page(
                        R.drawable.ic_tab_schedule,
                        stringResource(R.string.intro_times_title),
                        stringResource(R.string.intro_times_body),
                    )
                    IntroPage.FOLLOW -> Page(
                        R.drawable.ic_filter,
                        stringResource(R.string.intro_follow_title),
                        stringResource(
                            R.string.intro_follow_body,
                            t.mobile("schedule"),
                            t.mobile("filter"),
                            t.mobile("show_all_heats"),
                        ),
                    )
                    IntroPage.COUNTING -> Page(
                        R.drawable.ic_group,
                        stringResource(R.string.intro_counting_title),
                        state.privacyNote.orEmpty(),
                    ) {
                        CountingSwitch(state.counting, null, model::setCounting)
                        Text(
                            stringResource(R.string.intro_counting_where),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            Dots(pager.currentPage, pages.size)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { if (last) model.finishIntro() else goTo(pager.currentPage + 1) }) {
                    Text(stringResource(if (last) R.string.intro_done else R.string.intro_next))
                }
            }
        }
    }
}

@Composable
private fun Page(icon: Int, title: String, body: String, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body.isNotEmpty()) BodyWithIcons(body)
        extra()
    }
}

/**
 * The controls a page names, drawn as they look on screen: `{filter}` in the text becomes
 * the filter's own icon, `{plusminus}` the schedule's `±`. A name not listed stays as
 * written, so a server's text with braces in it is left alone.
 */
internal val INTRO_ICONS = mapOf(
    "filter" to R.drawable.ic_filter,
    "gear" to R.drawable.ic_settings,
    "bell" to R.drawable.ic_notifications,
)
internal const val PLUS_MINUS = "plusminus"
private val TOKEN = Regex("""\{(\w+)\}""")

/** What TalkBack reads: the words alone, since each names its control already — except `±`. */
internal fun spokenIntro(text: String): String = SPOKEN_TOKEN.replace(text) { m ->
    when (m.groupValues[2]) {
        PLUS_MINUS -> m.groupValues[1] + "\u00B1"
        in INTRO_ICONS -> ""
        else -> m.value
    }
}
private val SPOKEN_TOKEN = Regex("""(\s?)\{(\w+)\}""")

@Composable
private fun BodyWithIcons(body: String) {
    val primary = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        var at = 0
        TOKEN.findAll(body).forEach { m ->
            append(body.substring(at, m.range.first))
            val name = m.groupValues[1]
            when (name) {
                in INTRO_ICONS -> appendInlineContent(name, " ")
                PLUS_MINUS -> withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append("\u00B1") }
                else -> append(m.value)
            }
            at = m.range.last + 1
        }
        append(body.substring(at))
    }
    val inline = INTRO_ICONS.mapValues { (_, icon) ->
        InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(painterResource(icon), null, Modifier.fillMaxSize(), tint = primary)
        }
    }
    val spoken = spokenIntro(body)
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        inlineContent = inline,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

/** The page dots: drawn for the eye, and one "Page 2 of 4" to TalkBack rather than four glyphs. */
@Composable
private fun Dots(current: Int, count: Int) {
    val label = stringResource(R.string.intro_page, current + 1, count)
    Row(
        Modifier.fillMaxWidth().height(24.dp).clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val color = if (i ==
                current
            ) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
    }
    Spacer(Modifier.height(4.dp))
}
