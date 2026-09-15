package app.splouch.android.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.splouch.android.R
import app.splouch.core.schedule.Filter
import app.splouch.core.schedule.ScheduleFilterState
import app.splouch.core.schedule.Suggestion
import app.splouch.core.schedule.SuggestionType
import app.splouch.core.session.MeetState

/**
 * S-08..S-19: the filter, as the platform's full-screen dialog — an app bar carrying the
 * title and the confirming tick, and the body below it.
 *
 * The field is **not** a `SearchBar`. That control is built to filter the content on
 * screen, and this one filters nothing: it adds a term to a list, the way a mail composer
 * takes a recipient. Tapping a `SearchBar` would swap the bar for a search presentation
 * over a body with nothing new in it, which reads as a second window drawn to look like
 * the first.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(meet: MeetState, state: ScheduleFilterState, onChange: (ScheduleFilterState) -> Unit, onDismiss: () -> Unit) {
    val t = meet.strings
    var query by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // S-09: a local index over the start list, so every keystroke searches at once — no
    // debounce, because there is no server to spare.
    val suggestions: List<Suggestion> = remember(meet.suggestions, query) { meet.suggestions.search(query) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                TopAppBar(
                    title = { Text(t.mobile("filter"), style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.done))
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(t.mobile("search_placeholder")) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    trailingIcon = if (query.isEmpty()) null else ({
                        IconButton(onClick = { query = "" }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel))
                        }
                    }),
                    // A name is matched folded, so the keyboard has no business capitalising it.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        suggestions.firstOrNull { !state.contains(Filter(it.type, it.name)) }?.let {
                            onChange(state.add(Filter(it.type, it.name))); query = ""
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focus),
                )

                // Everything under the field stands down as soon as there is something to
                // search for: with a keyboard up, three rows of controls between the query
                // and its results is the sheet pushing the answer off screen. Keyed to the
                // query rather than to focus, so the tap changes nothing and the first
                // letter trades the controls for the results in one move.
                val searching = query.isNotEmpty()
                if (!searching) {
                    // S-11: a chip per active filter; its × removes it.
                    if (state.filters.isEmpty()) {
                        Text(
                            t.mobile("no_filters"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.filters.forEach { f ->
                                InputChip(
                                    selected = true,
                                    onClick = { onChange(state.remove(f)) },
                                    label = { Text(f.name) },
                                    // A chip is 32dp tall and they sit shoulder to shoulder, so
                                    // a slightly-off tap removed the wrong swimmer. This grows
                                    // the touch target to the 48dp floor and not the chip.
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                    trailingIcon = {
                                        Icon(
                                            painterResource(R.drawable.ic_close), null,
                                            Modifier.size(InputChipDefaults.AvatarSize),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    // S-16 and S-17, on one row — and in a FlowRow, so a long translation
                    // wraps instead of clipping.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.upcomingOnly,
                            onClick = { onChange(state.copy(upcomingOnly = !state.upcomingOnly)) },
                            label = { Text(t.mobile("upcoming_only")) },
                            leadingIcon = if (!state.upcomingOnly) null else ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }),
                        )
                        FilterChip(
                            selected = state.allHeats,
                            onClick = { onChange(state.copy(allHeats = !state.allHeats)) },
                            label = { Text(t.mobile("show_all_heats")) },
                            leadingIcon = if (!state.allHeats) null else ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }),
                        )
                    }
                    HorizontalDivider()
                }

                Box(Modifier.weight(1f)) {
                    if (suggestions.isEmpty()) {
                        // S-19: "no search results" is distinct from the list's "no matches".
                        if (query.isNotBlank()) {
                            Text(
                                t.mobile("no_search_results"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(suggestions, key = { it.type.name + "|" + it.name + "|" + it.club }) { s ->
                                SuggestionRow(s, state, t.mobile("swimmer"), t.mobile("club")) {
                                    onChange(state.add(Filter(s.type, s.name))); query = ""
                                }
                            }
                        }
                    }
                }

                if (!searching) {
                    HorizontalDivider()
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { confirmReset = true }, enabled = state.filters.isNotEmpty() || state.allHeats || state.upcomingOnly) {
                            Icon(painterResource(R.drawable.ic_reset), null, Modifier.size(18.dp))
                            Text(t.mobile("reset_filters"), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    // S-18: reset, behind a confirmation.
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(t.mobile("reset_confirm")) },
            confirmButton = { TextButton(onClick = { confirmReset = false; onChange(state.reset()) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** S-10: type, name and club; an already-added one is marked and inert. */
@Composable
private fun SuggestionRow(s: Suggestion, state: ScheduleFilterState, swimmerWord: String, clubWord: String, onAdd: () -> Unit) {
    val already = state.contains(Filter(s.type, s.name))
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    ListItem(
        modifier = if (already) Modifier else Modifier.clickable(onClick = onAdd),
        colors = if (!already) ListItemDefaults.colors() else ListItemDefaults.colors(headlineColor = dim, overlineColor = dim, supportingColor = dim),
        overlineContent = { Text(if (s.type == SuggestionType.SWIMMER) swimmerWord else clubWord) },
        headlineContent = { Text(s.name, maxLines = 1) },
        supportingContent = if (s.club.isEmpty()) null else ({ Text(s.club, maxLines = 1) }),
        trailingContent = if (!already) null else ({
            Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary)
        }),
    )
}
