package app.splouch.android.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.splouch.core.session.ServerAddress

/**
 * P-12, P-13, P-16 on Android 17: an app targeting API 37 reaches nothing on the pool's
 * wifi — no `_splouch._tcp` browse, no `.local` name, no `http` to a Pi — until the reader
 * grants `ACCESS_LOCAL_NETWORK`, a runtime permission. Below 37 the OS grants it with
 * `INTERNET`, and nothing here asks.
 *
 * Asked where the reader reaches for the pool's network, never on launch for a cloud: the
 * Search tap, adding or accepting an `http` address, and landing on a Pi already chosen. A
 * refusal changes nothing else: the action goes ahead and fails as an unreachable server
 * does — *No server found*, or the address's error.
 */
object LocalNetworkAccess {
    fun granted(context: Context): Boolean = Build.VERSION.SDK_INT < LOCAL_NETWORK_API ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

    /** The address is on the pool's network — a `.local` name or a private address — whatever its scheme. */
    fun isLocal(address: ServerAddress): Boolean = ServerAddress.isLocalName(address.host)

    /** Android 17, the first to enforce it for apps that target it. */
    private const val LOCAL_NETWORK_API = 37
}

/**
 * Runs the action once the reader has answered the system prompt, whatever the answer, or
 * at once when there is nothing to ask. One action at a time: a second ask while the prompt
 * is up replaces the first, which is what a second tap means.
 */
@Composable
fun rememberLocalNetworkAccess(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending[0]?.invoke()
        pending[0] = null
    }
    return remember(context, launcher) {
        { action ->
            if (LocalNetworkAccess.granted(context)) {
                action()
            } else {
                pending[0] = action
                launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
    }
}
