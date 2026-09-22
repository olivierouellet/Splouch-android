package app.splouch.android.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.splouch.android.R
import app.splouch.core.session.AddServerResult
import app.splouch.core.session.AppModel
import app.splouch.core.session.KnownServer
import app.splouch.core.session.RemovedServer
import app.splouch.core.session.ServerAddress
import app.splouch.core.session.UiState
import app.splouch.core.strings.BuiltInStrings
import kotlinx.coroutines.launch

/**
 * P-11..P-13: the server list — default, saved, the server's directory, nearby — and the
 * add-by-hand field. Every word here is about the app or the device, so it is a native
 * string resource, not a server string (app.md T-05).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSheet(model: AppModel, state: UiState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val invalidAddress = stringResource(R.string.invalid_address)
    val cleartextNotLocal = stringResource(R.string.cleartext_not_local)
    val notSplouch = stringResource(R.string.not_splouch)
    val unreachable = stringResource(R.string.server_unreachable)
    val removeLabel = stringResource(R.string.remove)
    val sheetSnackbar = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.server_removed)
    val undoLabel = stringResource(R.string.undo)

    // P-13: a swipe is one finger and a saved server is a typed address, so taking one
    // away has to be undoable. The snackbar carries the only way back; `restoreServer`
    // puts the row at its own index and re-selects it if it was in use.
    fun removeWithUndo(address: ServerAddress) {
        val removed: RemovedServer = model.removeServer(address) ?: return
        scope.launch {
            val r = sheetSnackbar.showSnackbar(removedMessage, undoLabel, withDismissAction = false)
            if (r == SnackbarResult.ActionPerformed) model.restoreServer(removed)
        }
    }

    fun add() {
        if (busy || text.isBlank()) return
        busy = true; error = null
        scope.launch {
            when (val r = model.addServer(text)) {
                is AddServerResult.Ok -> { text = ""; onDismiss() }
                AddServerResult.InvalidAddress -> error = invalidAddress
                AddServerResult.CleartextNotLocal -> error = cleartextNotLocal
                is AddServerResult.Unreachable -> error = if (r.reason == AppModel.NOT_SPLOUCH) notSplouch else unreachable
            }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
      Box {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.server))
            val nearby = state.servers.filter { it.source == KnownServer.Source.DISCOVERED }
            val others = state.servers.filter { it.source != KnownServer.Source.DISCOVERED }
            others.forEach {
                ServerRow(it, it.address == state.server, removeLabel,
                    onSelect = { model.selectServer(it.address); onDismiss() },
                    onRemove = if (it.source == KnownServer.Source.SAVED) ({ removeWithUndo(it.address) }) else null)
            }
            if (nearby.isNotEmpty()) {
                SectionHeader(stringResource(R.string.nearby))
                nearby.forEach {
                    ServerRow(it, it.address == state.server, removeLabel,
                        onSelect = { model.selectServer(it.address); onDismiss() }, onRemove = null)
                }
            }
            SectionHeader(stringResource(R.string.add_server))
            // P-13: one row, not three. The section header above already said what this is,
            // so the field submits itself — the return key, or the arrow that appears once
            // there is something to send — and the footer under it carries progress and error.
            OutlinedTextField(
                value = text, onValueChange = { text = it; error = null },
                placeholder = { Text(stringResource(R.string.server_placeholder), maxLines = 1) },
                singleLine = true, isError = error != null, enabled = !busy,
                supportingText = when {
                    error != null -> ({ Text(error!!) })
                    busy -> ({ Text(stringResource(R.string.checking)) })
                    else -> null
                },
                trailingIcon = {
                    when {
                        busy -> CircularProgressIndicator(Modifier.padding(12.dp))
                        text.isNotBlank() -> IconButton(onClick = { add() }) {
                            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.add_server))
                        }
                        else -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { add() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        SnackbarHost(sheetSnackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp))
      }
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerRow(s: KnownServer, selected: Boolean, removeLabel: String, onSelect: () -> Unit, onRemove: (() -> Unit)?) {
    val row = @Composable {
        ListItem(
            // `selectable` is what carries the selected state to the screen reader: the radio
            // button is a glyph, and a glyph says nothing out loud.
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
            colors = ListItemDefaults.colors(containerColor = BottomSheetDefaults.ContainerColor),
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            headlineContent = { Text(s.name, maxLines = 1) },
            supportingContent = { Text(s.address.display + (s.kind?.let { "  ·  ${it.wire}" } ?: ""), maxLines = 1) },
        )
    }
    if (onRemove == null) {
        row()
        return
    }
    val haptics = LocalHapticFeedback.current
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete), removeLabel,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = { row() },
    )
}

/**
 * T-08: one language per device, set where every meet is in view. The web picker shows
 * this same control, so its words are the server's.
 *
 * T-09's short/long control is **withdrawn** — see [app.splouch.core.session.Preferences.effectiveLabelStyle].
 * The stored choice is untouched, and returning the control is a matter of putting the
 * two rows back here and returning `labelStyle` from that property.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefsSheet(model: AppModel, state: UiState, onDismiss: () -> Unit) {
    val t = state.pickerStrings
    val locales = state.locales.ifEmpty { BuiltInStrings.locales() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            SectionHeader(t.mobile("language"))
            ChoiceRow(t.mobile("language_auto"), state.prefs.lang == null) { model.setLang(null) }
            locales.forEach { l -> ChoiceRow(l.name, state.prefs.lang == l.code) { model.setLang(l.code) } }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(label) },
    )
}
