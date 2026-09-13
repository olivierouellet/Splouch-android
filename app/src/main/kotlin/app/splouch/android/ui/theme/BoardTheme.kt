package app.splouch.android.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun BoardTheme(theme: Theme, content: @Composable () -> Unit) {
    val colors = remember(theme) { BoardColors(theme) }
    val fonts = remember(theme) { BoardFonts(theme) }
    CompositionLocalProvider(LocalBoardColors provides colors, LocalBoardFonts provides fonts) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = colors.bg, onBackground = colors.rowText,
                surface = colors.headerBg, onSurface = colors.rowText,
                surfaceVariant = colors.thBg, onSurfaceVariant = colors.thText,
                primary = colors.time, onPrimary = Color.Black,
                secondary = colors.scheduleEvent, outline = colors.headerBorder,
                surfaceContainer = colors.headerBg, surfaceContainerHigh = colors.rowEven, surfaceContainerLow = colors.rowOdd,
            ),
            content = content,
        )
    }
}

/**
 * L-17 / R-08: a long name shrinks to fit its cell, from [maxSize] down to [minSize];
 * only past that floor does it ellipsise. The platform measures, so no per-frame re-fit.
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
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontFamily = fontFamily, fontSize = maxSize, textAlign = textAlign, fontWeight = fontWeight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}
