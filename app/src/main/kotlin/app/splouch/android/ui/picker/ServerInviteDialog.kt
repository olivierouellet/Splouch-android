package app.splouch.android.ui.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.splouch.android.R
import app.splouch.core.session.AppModel
import app.splouch.core.session.InviteFailure
import app.splouch.core.session.ServerInvite

/**
 * P-16: a QR code named a server, and this is the yes it needs.
 *
 * It is a dialog and not a sheet or a row in the list on purpose. The reader was looking
 * at a poster a moment ago, not at this app — often the app was not even running — so
 * whatever is behind it is not what they came for, and the question has to be the whole
 * screen's business until it is answered. **The address is shown in full** (`display`,
 * which is host and port): the one thing the reader is actually consenting to is which
 * machine the app will talk to, and "Add this server?" without a name is not a question.
 *
 * Nothing is saved or dialled until the button is pressed; `AppModel.acceptInvite` then
 * runs `P-13`'s handshake and leaves this standing while it does, so a Pi that has gone
 * off the network says so *here* rather than closing and leaving the picker unchanged.
 */
@Composable
fun ServerInviteDialog(model: AppModel, invite: ServerInvite) {
    val address = invite.address
    val failure = invite.failure?.let { stringResource(it.message) }
    AlertDialog(
        // A press outside is a no, and a no is always allowed — except mid-handshake,
        // where dismissing would leave a request running with nothing on screen for it.
        onDismissRequest = { if (!invite.checking) model.dismissInvite() },
        icon = { Icon(painterResource(R.drawable.ic_server), null) },
        title = {
            Text(stringResource(when {
                // A link with nothing in it is a refusal, not an invitation with a red
                // line under it, so it does not borrow the list's "Add a server" header.
                address == null -> R.string.cannot_add_server
                invite.known -> R.string.switch_server_question
                else -> R.string.add_server_question
            }))
        },
        text = {
            Column {
                if (address != null) Text(address.display, style = MaterialTheme.typography.bodyLarge)
                if (failure != null) {
                    Text(
                        failure,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = if (address != null) Modifier.padding(top = 8.dp) else Modifier,
                    )
                }
            }
        },
        confirmButton = {
            if (address == null) {
                TextButton(onClick = { model.dismissInvite() }) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(onClick = { model.acceptInvite() }, enabled = !invite.checking) {
                    // The spinner takes the label's place rather than sitting beside it, so
                    // the dialog's buttons do not move under a finger that is already there.
                    if (invite.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (invite.known) R.string.switch_to else R.string.add))
                }
            }
        },
        dismissButton = if (address == null) null else ({
            TextButton(onClick = { model.dismissInvite() }, enabled = !invite.checking) {
                Text(stringResource(R.string.cancel))
            }
        }),
    )
}

/** The app's own words for each case (`T-05`): a link and a network are the device's business, not a meet's. */
private val InviteFailure.message: Int
    get() = when (this) {
        InviteFailure.BAD_LINK -> R.string.bad_server_link
        InviteFailure.CLEARTEXT_NOT_LOCAL -> R.string.cleartext_not_local
        InviteFailure.NOT_SPLOUCH -> R.string.not_splouch
        InviteFailure.UNREACHABLE -> R.string.server_unreachable
    }
