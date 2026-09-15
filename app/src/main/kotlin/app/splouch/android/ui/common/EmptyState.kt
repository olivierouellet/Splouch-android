package app.splouch.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The one shape every "there is nothing here" screen takes: P-04's no meets, S-07's no
 * schedule, S-19's no matches, R-01's waiting for results, and the server errors.
 *
 * The title is the server's sentence and nothing is invented to sit under it (T-05) —
 * a description the contract does not supply would be the app translating for the
 * server, which is the thing T-05 exists to stop. An [icon] is not a translation, so
 * these do carry one: an empty screen with a single line floating in it reads as a
 * failure to load rather than as an answer.
 *
 * [loading] puts a spinner where the icon goes, for the states that are genuinely waiting
 * on something rather than reporting an absence.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    loading: Boolean = false,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(36.dp))
            // Not spoken: the title underneath already says what it means, and a glyph
            // that repeats the sentence beside it is noise to a screen reader.
            icon != null -> Icon(
                icon, null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { Text(actionLabel) }
        }
    }
}
