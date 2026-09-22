package app.splouch.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.picker.PickerScreen
import app.splouch.android.ui.picker.ServerInviteDialog
import app.splouch.android.ui.shell.MeetShell
import app.splouch.android.ui.theme.BoardTheme
import app.splouch.android.ui.theme.SplouchTheme
import app.splouch.core.session.Appearance
import app.splouch.core.session.AppModel

/**
 * The two halves of the app, and the line between them (`parity.md` T-01).
 *
 * Outside a meet the components are Material's; inside one the board draws itself in the
 * meet's faces (`T-03`). **Which of the two palettes either half uses is decided here and
 * nowhere else** (`P-15`): the reader picks Dark, Light or Automatic, and the board reads
 * that back rather than voting with its own `bg`. It used to vote, and a spectator who
 * chose light got it until they opened a meet — which is where they were going.
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
    // P-15, and the one place the scheme is decided. `AUTO` hands the question to the OS,
    // which is what lets it move with the time of day.
    val dark = when (state.prefs.appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.AUTO -> isSystemInDarkTheme()
    }
    // The status and navigation bars sit over whichever of the two is on screen, and both
    // of them now answer the same question.
    SystemBarAppearance(dark = dark)

    val theme: @Composable (@Composable () -> Unit) -> Unit =
        if (meet != null) ({ content -> BoardTheme(dark, meet.theme) { content() } }) else ({ content -> SplouchTheme(dark) { content() } })

    theme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Opening a meet is a move forward and closing it a move back, so the two slide
            // along the shared axis rather than cutting. The picker used to be replaced by
            // the board between one frame and the next, which reads as a redraw rather than
            // as having gone somewhere. A meet is only ever reached from the picker, so
            // "in a meet or not" is the whole of the navigation state.
            AnimatedContent(
                targetState = meet != null,
                transitionSpec = {
                    val forward = targetState
                    val enter = { full: Int -> if (forward) full / 4 else -full / 4 }
                    val exit = { full: Int -> if (forward) -full / 4 else full / 4 }
                    (slideInHorizontally(tween(DurationMs), enter) + fadeIn(tween(DurationMs)))
                        .togetherWith(slideOutHorizontally(tween(DurationMs), exit) + fadeOut(tween(DurationMs)))
                },
                label = "meet",
            ) { inMeet ->
                // Read the meet off `state` rather than closing over it, so the outgoing
                // page keeps rendering the one it was showing for the length of the slide.
                val shown = state.meet
                if (inMeet && shown != null) MeetShell(model, state, shown, snackbar)
                else PickerScreen(model, state, images, snackbar)
            }
        }
        // P-16: a QR code named a server. It is asked over whichever half is on screen —
        // the app may have been launched straight into this from the camera, and it may
        // equally have been open on a running meet — so it belongs here and not inside
        // either one. `AppModel` holds the question, which is what lets it survive a
        // recreation while the reader is still reading it.
        state.invite?.let { ServerInviteDialog(model, it) }
    }
}

private const val DurationMs = 280

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
