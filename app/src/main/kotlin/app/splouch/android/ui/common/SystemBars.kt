package app.splouch.android.ui.common

import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Light glyphs over a dark screen and dark glyphs over a light one, in both system bars. */
@Composable
internal fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.barGlyphs(dark, view)
    }
}

/**
 * The same for a full-screen `Dialog`, which is a window of its own: drawn edge to edge
 * (`decorFitsSystemWindows = false`), its bars keep the platform's light glyphs whatever the
 * Activity's say, and over a light page they vanish (QA M-03). Dark is read off the page
 * actually under the bars — the meet's own colours on a meet's sheet.
 */
@Composable
internal fun DialogSystemBars() {
    val view = LocalView.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.barGlyphs(dark, view)
    }
}

private fun Window.barGlyphs(dark: Boolean, view: android.view.View) {
    WindowCompat.getInsetsController(this, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}
