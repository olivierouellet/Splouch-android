package app.splouch.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.splouch.android.BuildConfig
import app.splouch.android.R
import app.splouch.android.ui.common.rememberFocusReturn
import app.splouch.android.ui.picker.ServerSheet
import app.splouch.core.session.AppModel
import app.splouch.core.session.Appearance
import app.splouch.core.session.UiState
import app.splouch.core.strings.BuiltInStrings
import app.splouch.core.wire.LocaleEntry
import app.splouch.core.wire.ServerKind

/**
 * P-19: one full-screen destination in place of the picker's ⋮ menu. Sections Display,
 * Notifications (only while a meet has follows, N-12), Privacy (only while the server counts),
 * Server, About — the contract lists Server first;
 * the reader's own choices come first here (see `parity.md` P-19). Section
 * names and the toggle are the app's words (T-05); the privacy note and the disclaimer are
 * the server's. Back returns to the picker, whose list and query live in the model and are
 * untouched by anything here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: AppModel, state: UiState, onClose: () -> Unit, onReplayIntro: () -> Unit) {
    BackHandler(onBack = onClose)
    var showServers by remember { mutableStateOf(false) }
    val serverRow = rememberFocusReturn()
    var showLanguages by remember { mutableStateOf(false) }
    val languageRow = rememberFocusReturn()
    val locales = state.locales.ifEmpty { BuiltInStrings.locales() }
    val uri = LocalUriHandler.current
    val policy = state.server.httpUrl("/privacy")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.settings)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // ── Display: language (T-08), Appearance (P-15) ──
            // Language is one row naming the current choice and opening the list: the server
            // may offer many more languages than today's three, and inline they would push
            // everything below them off the screen. Appearance is three fixed choices, inline.
            Section(stringResource(R.string.settings_display), first = true)
            ListItem(
                modifier = Modifier.focusRequester(languageRow.requester).clickable { showLanguages = true },
                colors = transparent(),
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = {
                    Text(
                        state.prefs.lang?.let { code -> locales.firstOrNull { it.code == code }?.name ?: code }
                            ?: stringResource(R.string.language_auto),
                    )
                },
            )
            SubHeader(stringResource(R.string.appearance))
            listOf(
                R.string.appearance_dark to Appearance.DARK,
                R.string.appearance_light to Appearance.LIGHT,
                R.string.appearance_auto to Appearance.AUTO,
            ).forEach { (label, value) ->
                ChoiceRow(stringResource(label), state.prefs.appearance == value) { model.setAppearance(value) }
            }

            // ── Notifications (N-12): every followed meet, from every server, then Pause all ──
            if (state.followedMeets.isNotEmpty()) {
                Section(stringResource(R.string.notifications))
                state.followedMeets.forEach { meet ->
                    FollowedMeetSwitch(
                        name = state.followedName(meet),
                        server = model.followedServer(meet),
                        swimmers = meet.follows.swimmers.size,
                        on = meet.follows.enabled,
                    ) { model.setFollowsEnabled(meet, it) }
                }
                TextButton(
                    onClick = model::pauseAllFollows,
                    enabled = state.followedMeets.any { it.follows.enabled },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.notify_pause_all)) }
            }

            // ── Privacy (P-07): only while the server counts; the choice is kept either way ──
            if (state.countingOffered) {
                Section(stringResource(R.string.settings_privacy))
                CountingSwitch(state.counting, state.privacyNote, model::setCounting)
                LinkRow(stringResource(R.string.privacy_policy)) { uri.openUri(policy) }
            }

            // ── Server (P-11..P-13): a row to the existing sheet ──
            Section(stringResource(R.string.server))
            ListItem(
                modifier = Modifier.focusRequester(serverRow.requester).clickable { showServers = true },
                colors = transparent(),
                leadingContent = { Icon(painterResource(R.drawable.ic_server), null) },
                headlineContent = {
                    Text(state.serverInfo?.name?.ifBlank { null } ?: state.server.display, maxLines = 1)
                },
                supportingContent = { Text(state.server.display, maxLines = 1) },
            )

            // ── About: P-06 in full, the policy, P-20's replay, the version ──
            Section(stringResource(R.string.settings_about))
            state.disclaimer?.let {
                Text(
                    it.full,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // A Pi has no policy page and no attendance to count; a cloud always has both.
            if (state.kind == ServerKind.CLOUD) {
                LinkRow(stringResource(R.string.privacy_policy)) { uri.openUri(policy) }
            }
            // Only with the server's words in hand, as on first launch (P-20).
            if (state.picker.config != null) {
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onReplayIntro),
                    colors = transparent(),
                    headlineContent = { Text(stringResource(R.string.show_introduction)) },
                )
            }
            ListItem(
                colors = transparent(),
                headlineContent = { Text(stringResource(R.string.app_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }

    if (showLanguages) {
        LanguageDialog(state.prefs.lang, locales, model::setLang) {
            showLanguages = false
            languageRow.request()
        }
    }

    if (showServers) {
        ServerSheet(
            model,
            state,
            onDismiss = {
                showServers = false
                serverRow.request()
            },
            onChosen = {
                showServers = false
                onClose()
            },
        )
    }
}

/**
 * C-10's setting, the same one in Settings and on P-20's fourth page. The whole row is the
 * switch (`Role.Switch`), so TalkBack reads its label, its state and the server's note as
 * one; the `Switch` itself takes no taps of its own.
 */
@Composable
internal fun CountingSwitch(on: Boolean, note: String?, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        colors = transparent(),
        headlineContent = { Text(stringResource(R.string.privacy_count)) },
        supportingContent = note?.let { { Text(it) } },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    )
}

/**
 * N-12: one followed meet with its N-11 switch — the server under the name when it is not the
 * default, then the count or *Paused*. The whole row is the switch, as [CountingSwitch]'s is.
 */
@Composable
private fun FollowedMeetSwitch(
    name: String,
    server: String?,
    swimmers: Int,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val state = if (on) {
        pluralStringResource(R.plurals.notify_following, swimmers, swimmers)
    } else {
        stringResource(R.string.notify_paused_short)
    }
    ListItem(
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        colors = transparent(),
        headlineContent = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(server, state).joinToString(" · "), maxLines = 1) },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    )
}

/**
 * T-08's choices, the way an Android settings list offers one of many: a dialog of radio
 * rows that scrolls however many languages the server lists. A pick applies at once and
 * closes it, as a `ListPreference` does; Cancel, back or the scrim leave the choice as it was.
 */
@Composable
private fun LanguageDialog(
    current: String?,
    locales: List<LocaleEntry>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            // The dialog's own padding holds the text slot in 24dp; the rows draw their own.
            LazyColumn {
                item {
                    ChoiceRow(stringResource(R.string.language_auto), current == null) {
                        onPick(null)
                        onDismiss()
                    }
                }
                items(locales, key = { it.code }) { l ->
                    ChoiceRow(l.name, current == l.code) {
                        onPick(l.code)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = transparent(),
        headlineContent = { Text(label, color = MaterialTheme.colorScheme.primary) },
    )
}

/** X-06: `selectable` carries the current choice to TalkBack; the radio button is a glyph. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = transparent(),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(label) },
    )
}

/** A section: a divider above every one but the first, then its name, a heading to TalkBack (X-04). */
@Composable
private fun Section(text: String, first: Boolean = false) {
    if (!first) HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun transparent() = ListItemDefaults.colors(containerColor = Color.Transparent)
