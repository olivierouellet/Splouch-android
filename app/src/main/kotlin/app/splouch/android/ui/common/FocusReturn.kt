package app.splouch.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * X-10: the control that opened a sheet or a screen gets focus back once it has closed —
 * `P-06`'s line after its sheet, the Server row after the server sheet, the gear after
 * Settings. Put [requester] on the opener and call [request] where it closes.
 */
@Stable
class FocusReturn {
    val requester = FocusRequester()
    internal var pending by mutableStateOf(false)

    fun request() {
        pending = true
    }
}

/**
 * Waits two frames before asking: the sheet or screen has just left, and TalkBack re-homes
 * its own focus when that happens — a request made in the same frame is overtaken, and
 * TalkBack lands on the first control on the screen. An opener no longer on screen (scrolled
 * out of a list) is not an error; focus simply stays where the system put it.
 */
@Composable
fun rememberFocusReturn(): FocusReturn {
    val r = remember { FocusReturn() }
    LaunchedEffect(r.pending) {
        if (r.pending) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { r.requester.requestFocus() }
            r.pending = false
        }
    }
    return r
}
