package app.splouch.android.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable

/**
 * Material's own width breakpoints for the window this app is in — not the orientation,
 * and not the screen: a phone in landscape, a tablet and an unfolded foldable are three
 * different widths, and split-screen makes any of them narrow again.
 *
 * `Compact` (< 600dp) gets the bottom navigation bar; anything wider gets the rail
 * (see `parity.md` A-07).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun windowWidthClass(): WindowWidthSizeClass {
    val activity = LocalActivity.current ?: return WindowWidthSizeClass.Compact
    return calculateWindowSizeClass(activity).widthSizeClass
}

/** True where the tabs belong down the side rather than along the bottom. */
@Composable
fun useNavigationRail(): Boolean = windowWidthClass() != WindowWidthSizeClass.Compact
