package app.splouch.android.ui.picker

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.splouch.android.R
import app.splouch.core.session.MeetFilter
import app.splouch.core.session.countryName
import app.splouch.core.wire.MeetSummary

/**
 * P-21: the picker's filter, Android's way — a `ModalBottomSheet` of checkbox rows in three
 * sections, country, state/province and club, the club's opening with a field for a club the
 * list does not hold, by its official letters. Every tap writes straight to the stored filter,
 * so the list behind is already filtered when the sheet goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetFilterSheet(
    filter: MeetFilter,
    meets: List<MeetSummary>,
    lang: String,
    onChange: (MeetFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = filter.options(meets, lang)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            stringResource(R.string.meet_filter),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { onChange(MeetFilter()) }, enabled = filter.isActive) {
                            Text(stringResource(R.string.filter_clear))
                        }
                    },
                )
            }
            if (options.countries.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.filter_country)) }
                items(options.countries, key = { "c/$it" }) { code ->
                    CheckRow(countryName(code, lang), filter.has(code)) { onChange(filter.toggleCountry(code)) }
                }
            }
            if (options.provinces.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.filter_province)) }
                items(options.provinces, key = { "p/${it.country}/${it.name}" }) { p ->
                    CheckRow(p.label(lang), filter.has(p)) { onChange(filter.toggleProvince(p)) }
                }
            }
            item { SectionTitle(stringResource(R.string.filter_club)) }
            item(key = "k+") { ClubLettersField { onChange(filter.addClubLetters(it)) } }
            items(options.clubs, key = { "k/$it" }) { club ->
                CheckRow(club, filter.hasClub(club)) { onChange(filter.toggleClub(club)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * A club the list does not hold, typed by its official letters; [onAdd] gets them as typed, and
 * the filter keeps them cleaned. The field says what it wants under it, not only as a placeholder.
 */
@Composable
private fun ClubLettersField(onAdd: (String) -> Unit) {
    var letters by rememberSaveable { mutableStateOf("") }
    val add = {
        onAdd(letters)
        letters = ""
    }
    OutlinedTextField(
        value = letters,
        onValueChange = { letters = it },
        label = { Text(stringResource(R.string.filter_club_letters)) },
        supportingText = { Text(stringResource(R.string.filter_club_letters_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (MeetFilter.clubLetters(letters).isNotEmpty()) add() }),
        trailingIcon = {
            TextButton(onClick = add, enabled = MeetFilter.clubLetters(letters).isNotEmpty()) {
                Text(stringResource(R.string.filter_club_add))
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

/** The whole row is one checkbox to TalkBack, and its state is announced (X-06). */
@Composable
private fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = { Text(text) },
    )
}
