package app.splouch.android.ui

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.platform.LocalNetworkAccess
import app.splouch.android.platform.rememberLocalNetworkAccess
import app.splouch.android.ui.common.SystemBarAppearance
import app.splouch.android.ui.intro.IntroScreen
import app.splouch.android.ui.picker.PickerScreen
import app.splouch.android.ui.picker.ServerInviteDialog
import app.splouch.android.ui.settings.SettingsScreen
import app.splouch.android.ui.shell.MeetShell
import app.splouch.android.ui.theme.BoardTheme
import app.splouch.android.ui.theme.SplouchTheme
import app.splouch.core.session.AppModel
import app.splouch.core.session.Appearance
import app.splouch.core.session.UiState
import java.util.Locale

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
    NativeLanguage(state.prefs.lang) { RootContent(model, images, state) }
}

/**
 * T-05 and T-08: the app's own words — the server sheet, Language, Appearance — come from
 * its resources, which Android resolves in the device's language. A reader who chose
 * French in the app then read French from the server beside "Server" and "Language" in
 * English. A chosen language is laid over the resources here, for every screen and sheet;
 * Automatic leaves the device's. A language with no `values-*` of its own falls back to
 * English, as T-05 says the native table does.
 *
 * The localized context is no longer the Activity, so what Compose finds by walking
 * `LocalContext` up to it — the result registry a permission prompt is launched from
 * (`rememberLocalNetworkAccess`) — is handed down explicitly.
 */
@Composable
private fun NativeLanguage(lang: String?, content: @Composable () -> Unit) {
    if (lang == null) return content()
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val registry = checkNotNull(LocalActivityResultRegistryOwner.current)
    val localized = remember(context, base, lang) {
        val config = Configuration(base).apply { setLocale(Locale.forLanguageTag(lang)) }
        context.createConfigurationContext(config)
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalResources provides localized.resources,
        LocalActivityResultRegistryOwner provides registry,
        content = content,
    )
}

@Composable
private fun RootContent(model: AppModel, images: ImageCache, state: UiState) {
    val snackbar = remember { SnackbarHostState() }
    val meetGone = stringResource(R.string.meet_gone)

    // P-14: once per handshake, where the server name shows; never blocks anything.
    LaunchedEffect(state.contractNotice) {
        state.contractNotice?.let {
            snackbar.showSnackbar(it)
            model.dismissNotice()
        }
    }
    // Android 17: a pool's server already in use — stored from an earlier run, or a grant
    // since revoked in system settings — is reached only once the local network is granted.
    // Asked on landing on it, and the server is dialled again on the answer; a cloud never asks.
    val context = LocalContext.current
    val withLocalNetwork = rememberLocalNetworkAccess()
    LaunchedEffect(state.server) {
        if (LocalNetworkAccess.isLocal(state.server) && !LocalNetworkAccess.granted(context)) {
            withLocalNetwork(model::retry)
        }
    }

    // A-09: the meet went away; the picker is already back on screen.
    LaunchedEffect(state.meetGone) {
        if (state.meetGone) {
            snackbar.showSnackbar(meetGone)
            model.dismissMeetGone()
        }
    }

    // A-12: back was asked for and the meet list did not answer; the meet is still on
    // screen. The server's words, in the meet's language — the screen the reader is on.
    LaunchedEffect(state.pickerUnavailable) {
        if (state.pickerUnavailable) {
            val strings = state.meet?.strings ?: state.pickerStrings
            snackbar.showSnackbar(strings.mobile("picker_unavailable"))
            model.dismissPickerUnavailable()
        }
    }

    val meet = state.meet
    // P-19: Settings is a destination of the picker's, held here with the other three.
    // Saveable, so a rotation keeps the reader in it; a meet opening (a Pi chosen, a QR
    // code) closes it, so leaving that meet lands on the picker.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var refocusGear by remember { mutableStateOf(false) }
    LaunchedEffect(meet != null) { if (meet != null) settingsOpen = false }
    val screen = when {
        meet != null -> Screen.MEET
        // P-20 waits for the picker: never over a meet, and the model raises it only once
        // the server has answered.
        state.introOpen -> Screen.INTRO
        settingsOpen -> Screen.SETTINGS
        else -> Screen.PICKER
    }
    // The picker leaves composition while Settings or a meet is on screen; this keeps its
    // scroll position for the way back. Its query is the model's and survives regardless.
    val saved = rememberSaveableStateHolder()

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
        if (meet != null) {
            { content -> BoardTheme(dark, meet.theme) { content() } }
        } else {
            { content -> SplouchTheme(dark) { content() } }
        }

    theme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Opening a meet or Settings is a move forward and closing it a move back, so
            // the screens slide along the shared axis rather than cutting. A meet and
            // Settings are only ever reached from the picker, so depth is the whole of the
            // navigation state.
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState.depth > initialState.depth
                    val enter = { full: Int -> if (forward) full / 4 else -full / 4 }
                    val exit = { full: Int -> if (forward) -full / 4 else full / 4 }
                    (slideInHorizontally(tween(DURATION_MS), enter) + fadeIn(tween(DURATION_MS)))
                        .togetherWith(slideOutHorizontally(tween(DURATION_MS), exit) + fadeOut(tween(DURATION_MS)))
                },
                label = "screen",
            ) { shownScreen ->
                // Read the meet off `state` rather than closing over it, so the outgoing
                // page keeps rendering the one it was showing for the length of the slide.
                val shown = state.meet
                when {
                    shownScreen == Screen.MEET && shown != null -> MeetShell(model, state, shown, snackbar)
                    shownScreen == Screen.INTRO -> IntroScreen(model, state)
                    shownScreen == Screen.SETTINGS -> SettingsScreen(
                        model,
                        state,
                        onClose = {
                            settingsOpen = false
                            refocusGear = true // X-10
                        },
                        // P-20 ends on the picker, so the replay leaves Settings behind it.
                        onReplayIntro = {
                            settingsOpen = false
                            refocusGear = true
                            model.replayIntro()
                        },
                    )
                    else -> saved.SaveableStateProvider("picker") {
                        PickerScreen(
                            model,
                            state,
                            images,
                            snackbar,
                            onOpenSettings = { settingsOpen = true },
                            refocusSettings = refocusGear,
                            onRefocused = { refocusGear = false },
                        )
                    }
                }
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

private const val DURATION_MS = 280

/** The four places the app can be, by how far from the picker each sits. */
private enum class Screen(val depth: Int) { PICKER(0), SETTINGS(1), MEET(1), INTRO(2) }
