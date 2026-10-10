package app.splouch.android.ui.schedule

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.splouch.android.R
import app.splouch.android.platform.Push
import app.splouch.android.ui.common.DialogSystemBars
import app.splouch.core.follows.FollowLead
import app.splouch.core.follows.FollowedSwimmer
import app.splouch.core.follows.MeetFollows
import app.splouch.core.follows.PushPermission
import app.splouch.core.schedule.SuggestionType
import app.splouch.core.session.AppModel
import app.splouch.core.session.MeetState
import app.splouch.core.session.UiState

/**
 * N-02: who this device follows at the meet, how early it is told, and whether the
 * console's heat counts too — the platform's full-screen dialog, as the filter is.
 *
 * Every change is saved and sent at once (N-07), so the bar carries a close and nothing to
 * confirm. Swimmers are list rows with a remove action; the two ways of counting ahead are
 * a segmented button, the three values filter chips, the console's heat a switch row — the
 * Material forms for one-of-two, one-of-three and on/off.
 *
 * N-04: the system is asked right after the first swimmer is added, never on opening.
 * N-11: a switch at the top pauses the meet's notifications and keeps the list; while
 * paused, the swimmers stay editable and the rest is disabled.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationsSheet(model: AppModel, state: UiState, meet: MeetState, privacyUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val follows = meet.follows
    val on = follows.enabled
    var query by remember { mutableStateOf("") }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Push.markAsked(context)
        model.setPushPermission(if (granted) PushPermission.ALLOWED else PushPermission.REFUSED)
    }

    fun save(next: MeetFollows) {
        val first = follows.isEmpty && !next.isEmpty
        model.setFollows(next)
        if (first && state.pushPermission == PushPermission.NOT_ASKED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                model.setPushPermission(Push.permission(context))
            }
        }
    }

    // S-09's index, swimmers and relay teams only: a club is not someone whose heat comes up.
    val suggestions = remember(meet.suggestions, query) {
        meet.suggestions.search(query, limit = 40).filter { it.type == SuggestionType.SWIMMER }.take(20)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars()
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                        }
                    },
                    title = {
                        Text(
                            stringResource(R.string.notifications),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                    },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                // N-04: off for the app. The list stays; the way back is the system's page.
                if (state.pushPermission == PushPermission.REFUSED) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.notify_denied)) },
                            supportingContent = {
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                }) { Text(stringResource(R.string.notify_open_settings)) }
                            },
                        )
                        HorizontalDivider()
                    }
                }

                item {
                    ListItem(
                        modifier = Modifier.clickable { save(follows.copy(enabled = !on)) },
                        headlineContent = { Text(stringResource(R.string.notify_enabled)) },
                        supportingContent = if (on) null else ({ Text(stringResource(R.string.notify_paused)) }),
                        trailingContent = {
                            Switch(checked = on, onCheckedChange = { save(follows.copy(enabled = it)) })
                        },
                    )
                    HorizontalDivider()
                }

                item { Header(stringResource(R.string.notify_swimmers)) }
                if (follows.isEmpty && query.isEmpty()) {
                    item { Supporting(stringResource(R.string.notify_none)) }
                }
                items(follows.swimmers, key = { "f|" + it.name + "|" + it.club }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name, maxLines = 1) },
                        supportingContent = if (s.club.isEmpty()) null else ({ Text(s.club, maxLines = 1) }),
                        trailingContent = {
                            IconButton(onClick = { save(follows.remove(s)) }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    stringResource(R.string.notify_remove, s.name),
                                )
                            }
                        },
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.notify_add)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (query.isNotBlank()) {
                    if (suggestions.isEmpty()) {
                        item { Supporting(meet.strings.mobile("no_search_results")) }
                    }
                    items(suggestions, key = { "s|" + it.name + "|" + it.club }) { s ->
                        val swimmer = FollowedSwimmer(s.name, s.club)
                        val added = swimmer in follows.swimmers
                        ListItem(
                            modifier = if (added) {
                                Modifier
                            } else {
                                Modifier.clickable {
                                    save(follows.add(swimmer))
                                    query = ""
                                }
                            },
                            headlineContent = { Text(s.name, maxLines = 1) },
                            supportingContent = if (s.club.isEmpty()) null else ({ Text(s.club, maxLines = 1) }),
                            trailingContent = if (!added) {
                                null
                            } else {
                                (
                                    {
                                        Icon(
                                            painterResource(R.drawable.ic_check),
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    )
                            },
                        )
                    }
                }

                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Header(stringResource(R.string.notify_before))
                    val byHeats = follows.lead.byHeats
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        listOf(false to R.string.notify_by_minutes, true to R.string.notify_by_heats)
                            .forEachIndexed { i, (heats, label) ->
                                SegmentedButton(
                                    selected = byHeats == heats,
                                    onClick = {
                                        if (byHeats != heats) {
                                            val lead = if (heats) {
                                                FollowLead(true, FollowLead.HEAT_CHOICES.first())
                                            } else {
                                                FollowLead.STANDARD
                                            }
                                            save(follows.copy(lead = lead))
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    enabled = on,
                                ) { Text(stringResource(label)) }
                            }
                    }
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val choices = if (byHeats) FollowLead.HEAT_CHOICES else FollowLead.MINUTE_CHOICES
                        choices.forEach { n ->
                            val selected = follows.lead.value == n
                            FilterChip(
                                selected = selected,
                                onClick = { save(follows.copy(lead = FollowLead(byHeats, n))) },
                                enabled = on,
                                label = {
                                    Text(
                                        if (byHeats) {
                                            pluralStringResource(R.plurals.notify_heats_value, n, n)
                                        } else {
                                            stringResource(R.string.notify_minutes_value, n)
                                        },
                                    )
                                },
                                leadingIcon = if (!selected) {
                                    null
                                } else {
                                    (
                                        {
                                            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                                        }
                                        )
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }

                item {
                    ListItem(
                        modifier = Modifier.clickable(enabled = on) {
                            save(follows.copy(selected = !follows.selected))
                        },
                        headlineContent = { Text(stringResource(R.string.notify_selected)) },
                        supportingContent = { Text(stringResource(R.string.notify_selected_footer)) },
                        trailingContent = {
                            Switch(
                                checked = follows.selected,
                                onCheckedChange = { save(follows.copy(selected = it)) },
                                enabled = on,
                            )
                        },
                    )
                    HorizontalDivider()
                }

                // N-09: what leaves the phone, said where it is decided.
                item {
                    Supporting(stringResource(R.string.notify_privacy))
                    TextButton(
                        onClick = { uri.openUri(privacyUrl) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) { Text(stringResource(R.string.privacy_policy)) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
)

@Composable
private fun Supporting(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
