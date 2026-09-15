package app.splouch.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.splouch.android.ui.SplouchRoot
import app.splouch.core.session.ServerAddress

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars, with the icon polarity left to the system here and then set
        // per screen by `SystemBarAppearance`: the picker follows the device, a meet
        // follows its own palette, and those two disagree often enough to matter.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as SplouchApp
        // Debug builds only: `adb shell am start -n app.splouch.android/.MainActivity --es server http://10.0.2.2:5055`
        // starts on that server without touching stored preferences.
        if (savedInstanceState == null && (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent?.getStringExtra("server")?.let { ServerAddress.parseOrNull(it) }?.let { app.model.selectServer(it) }
        }
        setContent { SplouchRoot(app.model, app.images) }
    }
}
