package app.splouch.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.picker.PickerScreen
import app.splouch.android.ui.shell.MeetShell
import app.splouch.android.ui.theme.BoardTheme
import app.splouch.core.session.AppModel
import app.splouch.core.theme.Theme

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val meet = state.meet
        if (meet != null) {
            BoardTheme(meet.theme) { MeetShell(model, state, meet) }
        } else {
            BoardTheme(Theme.DEFAULT) { PickerScreen(model, state, images) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
