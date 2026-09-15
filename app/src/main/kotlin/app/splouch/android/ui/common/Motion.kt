package app.splouch.android.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the device has asked for animations to be removed — Accessibility → Remove
 * animations, which zeroes the animator duration scale.
 *
 * The board's own motion is information rather than decoration: `L-11`'s lock flash is
 * how a final time announces itself and `L-12`'s pulse is how a lane says its clock has
 * gone quiet, so neither is dropped here. What honours the setting is the motion that
 * only decorates — the picker's live dot, which the dot's own colour already states.
 */
@Composable
fun reduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
