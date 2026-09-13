package app.splouch.android.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.splouch.android.ui.ui
import app.splouch.core.session.AddServerResult
import app.splouch.core.session.AppModel
import app.splouch.core.session.KnownServer
import app.splouch.core.session.UiState
import app.splouch.core.strings.BuiltInStrings
import app.splouch.core.strings.Labels
import kotlinx.coroutines.launch

/** P-11..P-13: the server list — default, saved, the server's directory, nearby — and the add-by-hand field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSheet(model: AppModel, state: UiState, onDismiss: () -> Unit) {
    val t = state.pickerStrings
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun add() {
        if (busy || text.isBlank()) return
        busy = true; error = null
        scope.launch {
            when (val r = model.addServer(text)) {
                is AddServerResult.Ok -> { text = ""; onDismiss() }
                AddServerResult.InvalidAddress -> error = t.ui("invalid_address")
                AddServerResult.CleartextNotLocal -> error = t.ui("cleartext_not_local")
                is AddServerResult.Unreachable -> error = if (r.reason == "not a Splouch server") t.ui("not_splouch") else t.ui("server_unreachable")
            }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(t.ui("server"), fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            val nearby = state.servers.filter { it.source == KnownServer.Source.DISCOVERED }
            val others = state.servers.filter { it.source != KnownServer.Source.DISCOVERED }
            others.forEach { ServerRow(it, it.address == state.server, t.ui("remove"), onSelect = { model.selectServer(it.address); onDismiss() }, onRemove = if (it.source == KnownServer.Source.SAVED) ({ model.removeServer(it.address) }) else null) }
            if (nearby.isNotEmpty()) {
                Text(t.ui("nearby"), fontSize = 13.sp, color = Color(0xFF888888), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                nearby.forEach { ServerRow(it, it.address == state.server, t.ui("remove"), onSelect = { model.selectServer(it.address); onDismiss() }, onRemove = null) }
            }
            Spacer(Modifier.height(16.dp))
            Text(t.ui("add_server"), fontSize = 13.sp, color = Color(0xFF888888))
            OutlinedTextField(
                value = text, onValueChange = { text = it; error = null },
                placeholder = { Text(t.ui("server_placeholder"), fontSize = 13.sp) },
                singleLine = true, isError = error != null, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { add() }),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            error?.let { Text(it, color = Color(0xFFE57373), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(t.ui("cancel")) }
                Button(onClick = { add() }, enabled = !busy && text.isNotBlank()) { Text(if (busy) t.ui("checking") else t.ui("ok")) }
            }
        }
    }
}

@Composable
private fun ServerRow(s: KnownServer, selected: Boolean, removeLabel: String, onSelect: () -> Unit, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(s.name, fontSize = 15.sp)
            Text(s.address.display + (s.kind?.let { "  ·  ${it.wire}" } ?: ""), fontSize = 12.sp, color = Color(0xFF888888))
        }
        if (onRemove != null) TextButton(onClick = onRemove) { Text(removeLabel, fontSize = 12.sp) }
    }
}

/** T-08, T-09: one language and one label style per device, set where every meet is in view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefsSheet(model: AppModel, state: UiState, onDismiss: () -> Unit) {
    val t = state.pickerStrings
    val locales = state.locales.ifEmpty { BuiltInStrings.locales() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(t.ui("language"), fontSize = 13.sp, color = Color(0xFF888888))
            ChoiceRow(t.ui("prefs_auto"), state.prefs.lang == null) { model.setLang(null) }
            locales.forEach { l -> ChoiceRow(l.name, state.prefs.lang == l.code) { model.setLang(l.code) } }
            Spacer(Modifier.height(12.dp))
            Text(t.ui("prefs_labels"), fontSize = 13.sp, color = Color(0xFF888888))
            ChoiceRow(t.ui("prefs_auto"), state.prefs.labelStyle == null) { model.setLabelStyle(null) }
            ChoiceRow(t.ui("prefs_short"), state.prefs.labelStyle == Labels.SHORT) { model.setLabelStyle(Labels.SHORT) }
            ChoiceRow(t.ui("prefs_long"), state.prefs.labelStyle == Labels.LONG) { model.setLabelStyle(Labels.LONG) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(t.ui("done")) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 15.sp)
    }
}
