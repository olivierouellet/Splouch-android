package app.splouch.android.ui.theme

import android.os.Build
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.splouch.android.R
import app.splouch.core.theme.Theme

/** The meet's palette (app.md T-01, T-02), every key resolved through the documented defaults (T-07). */
class BoardColors(private val theme: Theme) {
    private fun c(key: String): Color = Color(Theme.parseArgb(theme.color(key)) ?: Theme.parseArgb(Theme.DEFAULT_COLORS.getValue(key))!!)

    val bg = c("bg")
    val headerBg = c("header_bg")
    val headerBorder = c("header_border")
    val headerLabel = c("header_label")
    val headerValue = c("header_value")
    val thText = c("th_text")
    val thBg = c("th_bg")
    val rowOdd = c("row_odd")
    val rowEven = c("row_even")
    val rowText = c("row_text")
    val time = c("time")
    val deltaBetter = c("delta_better")
    val deltaWorse = c("delta_worse")
    val scheduleEvent = c("schedule_event")
    val scheduleTime = c("schedule_time")
    val scheduleName = c("schedule_name")
    val scheduleClub = c("schedule_club")

    /** The web's `.time-running` grey. */
    val timeRunning = Color(0xFFA0A0A0)

    /**
     * Whether this meet paints a dark board. The operator sets thirteen colours and no
     * light/dark flag, so the background is the one that decides: everything Material
     * draws over the meet — its sheets, dialogs and menus — follows the meet rather than
     * the device, or a light meet ends up with dark chrome around a white board.
     */
    val isDark: Boolean = bg.luminance() < 0.5f
}

/** The three font roles (T-03), from the six bundled faces; an unknown name falls back to the system monospace. */
class BoardFonts(theme: Theme) {
    val family: FontFamily = resolve(theme.font("family"))
    val digits: FontFamily = resolve(theme.font("digits"))
    val timing: FontFamily = resolve(theme.font("timing"))

    companion object {
        fun resolve(name: String): FontFamily = when (name.trim().lowercase().replace(" ", "")) {
            "overpassmono" -> FontFamily(Font(R.font.overpass_mono))
            "dseg7classic" -> FontFamily(Font(R.font.dseg7_classic))
            "dseg14classic" -> FontFamily(Font(R.font.dseg14_classic))
            "sharetechmono" -> FontFamily(Font(R.font.share_tech_mono))
            "orbitron" -> FontFamily(Font(R.font.orbitron))
            "robotomono" -> FontFamily(Font(R.font.roboto_mono))
            else -> FontFamily.Monospace
        }
    }
}

val LocalBoardColors = staticCompositionLocalOf { BoardColors(Theme.DEFAULT) }
val LocalBoardFonts = staticCompositionLocalOf { BoardFonts(Theme.DEFAULT) }

/**
 * The chrome outside a meet: the picker, the server and language sheets, the errors.
 *
 * There is no meet here and so no palette to render (T-01 starts at `settings`), so the
 * components are Material's and the colours are roles rather than the `#0d0d0d` greys the
 * web picker paints — that is stylesheet, and chrome is the platform's (§0.4).
 *
 * **Dark, though, and not the device's choice.** Every theme an operator ships paints a
 * dark board, so a light picker would hand a spectator a white list and then drop them
 * onto black the moment they tapped a meet. Until there is a light board to match — the
 * palette is the operator's (`T-01`), so that is their call and not ours — this follows
 * the board rather than the system. Dynamic colour still applies where the platform
 * offers it: it is the user's wallpaper tint, in its dark form.
 */
@Composable
fun SplouchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context)
        else darkColorScheme()
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

/**
 * Inside a meet: the board's own paint (T-01..T-03) on the composition locals, and a
 * Material scheme derived from the same palette so every platform surface drawn over the
 * board — the filter sheet, its dialogs, the navigation bar — belongs to the same screen.
 */
@Composable
fun BoardTheme(theme: Theme, content: @Composable () -> Unit) {
    val colors = remember(theme) { BoardColors(theme) }
    val fonts = remember(theme) { BoardFonts(theme) }
    val scheme = remember(colors) { meetScheme(colors) }
    CompositionLocalProvider(LocalBoardColors provides colors, LocalBoardFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

/**
 * The meet's thirteen colours mapped onto the Material roles that matter. Only the roles
 * the operator actually supplies are overridden; the rest come from the baseline scheme
 * for the light or dark the background implies, so a component we have not thought about
 * still draws legibly.
 */
private fun meetScheme(c: BoardColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    val onAccent = if (c.time.luminance() < 0.5f) Color.White else Color.Black
    return base.copy(
        background = c.bg, onBackground = c.rowText,
        surface = c.bg, onSurface = c.rowText,
        surfaceVariant = c.thBg, onSurfaceVariant = c.thText,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.rowOdd,
        surfaceContainer = c.headerBg, surfaceContainerHigh = c.rowEven, surfaceContainerHighest = c.headerBg,
        primary = c.time, onPrimary = onAccent,
        primaryContainer = c.headerBg, onPrimaryContainer = c.headerValue,
        // The navigation bar's selected pill and the chips draw on this pair, so it has
        // to read as a selection against `surfaceContainer` — `th_bg` is usually the same
        // colour as the bar itself, which left the selected tab with no indicator at all.
        secondary = c.scheduleEvent, onSecondary = onAccent,
        secondaryContainer = c.headerBorder, onSecondaryContainer = c.time,
        outline = c.headerBorder, outlineVariant = c.headerBorder,
    )
}

/**
 * L-17 / R-08: a long name shrinks to fit its cell, from [maxSize] down to [minSize];
 * only past that floor does it ellipsise. The platform measures, so no per-frame re-fit.
 *
 * [maxLines] is 1 for a lane's name, which shares a row with five other cells and has to
 * hold its line. The event name is the exception: it has a header to itself, so it is
 * given two and shrinks only once it has used both.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalBoardColors.current.rowText,
    fontFamily: FontFamily? = LocalBoardFonts.current.family,
    maxSize: TextUnit = 16.sp,
    minSize: TextUnit = 8.sp,
    textAlign: TextAlign = TextAlign.Start,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontFamily = fontFamily, fontSize = maxSize, textAlign = textAlign, fontWeight = fontWeight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}
