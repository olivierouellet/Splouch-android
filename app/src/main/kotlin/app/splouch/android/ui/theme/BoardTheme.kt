package app.splouch.android.ui.theme

import android.os.Build
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

/**
 * The board's palette, in the one of two the reader chose (`P-15`).
 *
 * **Not the meet's** — see `Theme` and `parity.md` T-01 for the departure and what it
 * costs. Both tables are the server's own, so a board still looks like Splouch either way.
 */
class BoardColors(val isDark: Boolean) {
    private val table = Theme.palette(isDark)
    private fun c(key: String): Color = Color(Theme.parseArgb(table.getValue(key))!!)

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

    /**
     * A running time (`L-11`), dimmed against the row it sits on rather than against an
     * assumed black. It was a fixed `#A0A0A0` — the web's `.time-running` grey, from a
     * board that was only ever dark — which on a light board is pale grey on near-white.
     * At 70% of `row_text` it lands within a couple of percent of the old grey on the dark
     * board and stays readable on the light one.
     */
    val timeRunning = rowText.copy(alpha = 0.7f)
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

val LocalBoardColors = staticCompositionLocalOf { BoardColors(isDark = true) }
val LocalBoardFonts = staticCompositionLocalOf { BoardFonts(Theme.DEFAULT) }

/**
 * The chrome outside a meet: the picker, the server and language sheets, the errors.
 *
 * There is no meet here and so no palette to render (T-01 starts at `settings`), so the
 * components are Material's and the colours are roles rather than the `#0d0d0d` greys the
 * web picker paints — that is stylesheet, and chrome is the platform's (§0.4).
 *
 * **[dark] is the reader's choice, resolved once in `SplouchRoot` (`P-15`).** This used to
 * be pinned dark on the grounds that every theme an operator ships paints a dark board, so
 * a light picker would drop a spectator onto black the moment they tapped a meet. There is
 * a light board now and the reader picks it, so the pin is gone and both halves of the app
 * answer the same question. Dynamic colour still applies where the platform offers it: it
 * is the user's wallpaper tint, in whichever form was asked for.
 */
@Composable
fun SplouchTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> if (dark) darkColorScheme() else lightColorScheme()
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

/**
 * Inside a meet: the board's paint on the composition locals, and a Material scheme derived
 * from the same palette so every platform surface drawn over the board — the filter sheet,
 * its dialogs, the navigation bar — belongs to the same screen.
 *
 * [dark] is the reader's (`P-15`), read back from where `SplouchRoot` resolved it rather
 * than decided here. [theme] is still the **meet's**, and still supplies the three faces
 * (`T-03`); only its colours go unread.
 */
@Composable
fun BoardTheme(dark: Boolean, theme: Theme, content: @Composable () -> Unit) {
    val colors = remember(dark) { BoardColors(dark) }
    val fonts = remember(theme) { BoardFonts(theme) }
    val scheme = remember(colors) { meetScheme(colors) }
    CompositionLocalProvider(LocalBoardColors provides colors, LocalBoardFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

/**
 * The board's colours mapped onto the Material roles that matter. Only the roles the
 * palette actually speaks to are overridden; the rest come from the baseline scheme for
 * the light or dark in effect, so a component we have not thought about still draws legibly.
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
    /**
     * The floor. A caller sizing from a height it measured must derive this from the *same*
     * unit as [maxSize] — a raw `sp` floor under a `dp`-pinned maximum inverts the range the
     * moment the device's font-size setting goes above 1, and the text is then drawn bigger
     * than the cell was measured for rather than smaller. [coerceAtMost] is the belt to that
     * braces: an inverted range silently mis-sizes instead of failing.
     */
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
        autoSize = TextAutoSize.StepBased(minFontSize = if (minSize > maxSize) maxSize else minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}
