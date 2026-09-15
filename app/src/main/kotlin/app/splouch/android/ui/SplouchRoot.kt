package app.splouch.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.picker.PickerScreen
import app.splouch.android.ui.shell.MeetShell
import app.splouch.android.ui.theme.BoardTheme
import app.splouch.android.ui.theme.SplouchTheme
import app.splouch.core.session.AppModel
import app.splouch.core.theme.Theme

/**
 * The two halves of the app, and the line between them (`parity.md` T-01).
 *
 * Outside a meet there is no palette to render, so the picker and its sheets are the
 * device's theme. Inside one, the meet paints the board and everything Material draws
 * over it follows the same light or dark.
 */
@Composable
fun SplouchRoot(model: AppModel, images: ImageCache) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val meetGone = stringResource(R.string.meet_gone)

    // P-14: once per handshake, where the server name shows; never blocks anything.
    LaunchedEffect(state.contractNotice) {
        state.contractNotice?.let { snackbar.showSnackbar(it); model.dismissNotice() }
    }
    // A-09: the meet went away; the picker is already back on screen.
    LaunchedEffect(state.meetGone) {
        if (state.meetGone) { snackbar.showSnackbar(meetGone); model.dismissMeetGone() }
    }

    val meet = state.meet
    // The status and navigation bars sit over whichever of the two is on screen.
    SystemBarAppearance(dark = if (meet != null) isDarkBoard(meet.theme) else isSystemInDarkTheme())

    val theme: @Composable (@Composable () -> Unit) -> Unit =
        if (meet != null) ({ content -> BoardTheme(meet.theme) { content() } }) else ({ content -> SplouchTheme { content() } })

    theme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (meet != null) MeetShell(model, state, meet) else PickerScreen(model, state, images)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** The meet's background decides, the same way `BoardColors.isDark` does. */
private fun isDarkBoard(theme: Theme): Boolean {
    val argb = Theme.parseArgb(theme.color("bg")) ?: return true
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    return 0.299 * r + 0.587 * g + 0.114 * b < 0.5
}

/** Light glyphs over a dark screen and dark glyphs over a light one, in both system bars. */
@Composable
private fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
