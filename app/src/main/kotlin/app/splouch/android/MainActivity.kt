package app.splouch.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.splouch.android.ui.SplouchRoot
import app.splouch.core.session.Appearance
import app.splouch.core.session.ServerAddress
import app.splouch.core.theme.Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars, with the icon polarity left to the system here and then set
        // per screen by `SystemBarAppearance`: the picker follows the device, a meet
        // follows its own palette, and those two disagree often enough to matter.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as SplouchApp
        // P-15. `themes.xml` paints the launch window before Compose exists, and it cannot
        // read a preference, so it carries the default (dark). A reader who chose Light got
        // a black flash on every cold start; repaint the window once the stored choice is
        // known. `AUTO` asks the device, the same question `SplouchRoot` asks Compose.
        val dark = when (app.model.current.prefs.appearance) {
            Appearance.DARK -> true
            Appearance.LIGHT -> false
            Appearance.AUTO ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val bg = Theme.parseArgb(Theme.palette(dark).getValue("bg"))!!.toInt()
        window.setBackgroundDrawable(ColorDrawable(bg))
        // Only on a fresh start: an Intent survives process death and comes back with the
        // restored Activity, and a QR code must not ask its question a second time because
        // the system reclaimed the app while the reader was reading it.
        if (savedInstanceState == null) consume(intent)
        setContent { SplouchRoot(app.model, app.images) }
    }

    /**
     * The Activity is `singleTask`, so a scan made while the app is already open arrives
     * here rather than stacking a second copy on top of a running meet.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    /**
     * P-16: `https://splouch.ca/add?server=…` from the camera. The model does the parsing —
     * the host it will accept is its own default server's and nothing here decides that —
     * and raises the prompt; nothing is saved or dialled before the reader agrees.
     *
     * Each one is **taken out of the Intent once read**. An Activity keeps the Intent that
     * started it and hands it back on every recreation, so a link left in place would add
     * the server again on the next rotation that outlives the process.
     */
    private fun consume(intent: Intent?) {
        intent ?: return
        val app = application as SplouchApp
        // Debug builds only: `adb shell am start -n app.splouch.android/.MainActivity --es server http://10.0.2.2:5055`
        // starts on that server without touching stored preferences.
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent.getStringExtra("server")?.let { ServerAddress.parseOrNull(it) }?.let {
                intent.removeExtra("server")
                app.model.selectServer(it)
            }
        }
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { url ->
                intent.data = null
                app.model.openServerLink(url)
            }
        }
    }
}
