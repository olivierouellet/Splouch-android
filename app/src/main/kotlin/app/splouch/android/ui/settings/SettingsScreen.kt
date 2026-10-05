package app.splouch.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.splouch.android.BuildConfig
import app.splouch.android.R
import app.splouch.android.ui.common.rememberFocusReturn
import app.splouch.android.ui.picker.ServerSheet
import app.splouch.core.session.AppModel
import app.splouch.core.session.Appearance
import app.splouch.core.session.UiState
import app.splouch.core.strings.BuiltInStrings
import app.splouch.core.wire.ServerKind

/**
 * P-19: one full-screen destination in place of the picker's ⋮ menu, sections in the
 * contract's order — Server, Display, Privacy (only while the server counts), About. Section
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
            // ── Server (P-11..P-13): a row to the existing sheet ──
            Section(stringResource(R.string.server), first = true)
            ListItem(
                modifier = Modifier.focusRequester(serverRow.requester).clickable { showServers = true },
                colors = transparent(),
                leadingContent = { Icon(painterResource(R.drawable.ic_server), null) },
                headlineContent = {
                    Text(state.serverInfo?.name?.ifBlank { null } ?: state.server.display, maxLines = 1)
                },
                supportingContent = { Text(state.server.display, maxLines = 1) },
            )

            // ── Display: language (T-08), Appearance (P-15) ──
            Section(stringResource(R.string.settings_display))
            SubHeader(stringResource(R.string.language))
            val locales = state.locales.ifEmpty { BuiltInStrings.locales() }
            ChoiceRow(stringResource(R.string.language_auto), state.prefs.lang == null) { model.setLang(null) }
            locales.forEach { l -> ChoiceRow(l.name, state.prefs.lang == l.code) { model.setLang(l.code) } }
            SubHeader(stringResource(R.string.appearance))
            listOf(
                R.string.appearance_dark to Appearance.DARK,
                R.string.appearance_light to Appearance.LIGHT,
                R.string.appearance_auto to Appearance.AUTO,
            ).forEach { (label, value) ->
                ChoiceRow(stringResource(label), state.prefs.appearance == value) { model.setAppearance(value) }
            }

            // ── Privacy (P-07): only while the server counts; the choice is kept either way ──
            if (state.countingOffered) {
                Section(stringResource(R.string.settings_privacy))
                CountingSwitch(state.counting, state.privacyNote, model::setCounting)
                LinkRow(stringResource(R.string.privacy_policy)) { uri.openUri(policy) }
            }

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
