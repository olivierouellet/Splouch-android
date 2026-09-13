package app.splouch.android.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.splouch.android.ui.theme.LocalBoardColors
import app.splouch.android.ui.theme.LocalBoardFonts
import app.splouch.android.ui.ui
import app.splouch.core.schedule.Filter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.session.AppModel
import app.splouch.core.session.MeetState
import app.splouch.core.wire.Suggestion
import app.splouch.core.wire.SuggestionType
import kotlinx.coroutines.delay

/** S-08..S-19: the full-screen filter sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(model: AppModel, meet: MeetState, state: ScheduleFilterState, onChange: (ScheduleFilterState) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalBoardColors.current
    val fonts = LocalBoardFonts.current
    val t = meet.strings
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var confirmReset by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // S-09: debounced ~220ms.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(220)
        suggestions = model.suggestions(q)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().background(colors.headerBg).padding(10.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(t.mobile("search_placeholder"), color = colors.scheduleClub, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                TextButton(onClick = onDismiss) { Text("✓", color = Color(0xFF4CAF50), fontSize = 22.sp) }
            }
            HorizontalDivider(color = colors.headerBorder)
            Row(Modifier.fillMaxWidth().padding(12.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // S-17 and S-16.
                FilterChip(selected = state.upcomingOnly, onClick = { onChange(state.copy(upcomingOnly = !state.upcomingOnly)) }, label = { Text(t.mobile("upcoming_only"), fontSize = 13.sp) }, modifier = Modifier.weight(1f))
                FilterChip(selected = state.allHeats, onClick = { onChange(state.copy(allHeats = !state.allHeats)) }, label = { Text("☰ " + t.mobile("show_all_heats"), fontSize = 13.sp) }, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = colors.headerBorder)
            // S-11: chips; tapping one removes it.
            FlowRow(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.filters.isEmpty()) {
                    Text(t.ui("no_filters"), color = colors.scheduleClub, fontSize = 13.sp, fontFamily = fonts.family)
                }
                state.filters.forEach { f ->
                    val (bg, fg) = chipColors(f.type)
                    Text("${f.name}  ×", color = fg, fontSize = 13.sp, fontFamily = fonts.family,
                        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).clickable { onChange(state.remove(f)) }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            HorizontalDivider(color = colors.headerBorder)
            Box(Modifier.weight(1f)) {
                if (suggestions.isEmpty()) {
                    // S-19: "no search results" is distinct from "no matches" on the list.
                    if (query.isNotBlank()) Text(t.ui("no_search_results"), color = colors.scheduleClub, fontSize = 14.sp, fontFamily = fonts.family, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(suggestions, key = { it.type.wire + "|" + it.name + "|" + it.club }) { s ->
                            val f = Filter(s.type, s.name)
                            val already = state.contains(f)
                            val (bg, fg) = chipColors(s.type)
                            Row(
                                Modifier.fillMaxWidth().background(colors.bg)
                                    // S-10: already-added suggestions are marked and inert.
                                    .then(if (already) Modifier else Modifier.clickable { onChange(state.add(f)); query = "" })
                                    .padding(16.dp, 13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(t.ui(if (s.type == SuggestionType.SWIMMER) "swimmer" else "club").uppercase(), color = fg, fontSize = 10.sp, letterSpacing = 1.sp,
                                    modifier = Modifier.background(bg, RoundedCornerShape(3.dp)).padding(5.dp, 2.dp))
                                Text(s.name, color = colors.rowText.copy(alpha = if (already) 0.45f else 1f), fontSize = 15.sp, fontFamily = fonts.family, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (s.club.isNotEmpty()) Text(s.club, color = colors.scheduleClub, fontSize = 12.sp, fontFamily = fonts.family, maxLines = 1)
                                if (already) Text("✓", color = colors.scheduleEvent, fontSize = 15.sp)
                            }
                            HorizontalDivider(color = colors.headerBorder)
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.headerBorder)
            Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = { confirmReset = true }) { Text("↺ " + t.mobile("reset_filters"), color = colors.scheduleClub, fontSize = 13.sp) }
            }
        }
    }
    // S-18: reset, behind a confirmation.
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(t.mobile("reset_confirm")) },
            confirmButton = { TextButton(onClick = { confirmReset = false; onChange(state.reset()) }) { Text(t.ui("ok")) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(t.ui("cancel")) } },
        )
    }
}

private fun chipColors(type: SuggestionType): Pair<Color, Color> =
    if (type == SuggestionType.SWIMMER) Color(0xFF1A3A5C) to Color(0xFF7EC8F5) else Color(0xFF3A2800) to Color(0xFFF5C040)
