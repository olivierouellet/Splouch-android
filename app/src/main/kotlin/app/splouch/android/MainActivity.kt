package app.splouch.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import app.splouch.android.ui.SplouchRoot
import app.splouch.core.session.ServerAddress

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The board is dark whatever the meet's theme says about rows: light icons in both bars.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
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
