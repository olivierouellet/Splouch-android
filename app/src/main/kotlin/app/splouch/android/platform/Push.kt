package app.splouch.android.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.splouch.android.BuildConfig
import app.splouch.android.MainActivity
import app.splouch.android.R
import app.splouch.android.SplouchApp
import app.splouch.core.follows.FollowStore
import app.splouch.core.follows.HeatFocus
import app.splouch.core.follows.MeetFollows
import app.splouch.core.follows.PushPermission
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * The device's side of heat notifications (app.md §10): Firebase, set up from this build's
 * own configuration; the two channels; the permission as it stands; and posting what the
 * meet's cloud node sends.
 *
 * The server sends **data** messages (api.md §5.13), so every notification is posted here,
 * in the channel for its kind — *Upcoming heats* and *Heat on the console* — which is what
 * lets a spectator tune the two apart in Android's settings, the platform's own control.
 */
object Push {
    const val CHANNEL_UPCOMING = "heat_upcoming"
    const val CHANNEL_SELECTED = "heat_selected"
    const val ACTION_OPEN_HEAT = "app.splouch.android.OPEN_HEAT"
    const val EXTRA_MEET = "meet_id"
    const val EXTRA_EVENT = "event"
    const val EXTRA_HEAT = "heat"

    /** Whether this build carries a Firebase project at all (`N-01`). Sets it up once. */
    fun start(context: Context): Boolean {
        if (BuildConfig.FIREBASE_APP_ID.isBlank()) return false
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        createChannels(context)
        return true
    }

    /**
     * Both channels high importance: a heat in five minutes is a heads-up, not a line in the
     * shade (`N-04`). Created at start, so they are in Settings before the first arrives.
     */
    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_UPCOMING,
                    context.getString(R.string.channel_upcoming),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.channel_upcoming_description) },
                NotificationChannel(
                    CHANNEL_SELECTED,
                    context.getString(R.string.channel_selected),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.channel_selected_description) },
            ),
        )
    }

    /**
     * `N-04`, as it stands now. Android 13+ only says granted or not, so "not asked yet" is
     * remembered here: refused after asking is [PushPermission.REFUSED], which the sheet
     * answers with the system's settings page rather than a prompt Android may not show.
     */
    fun permission(context: Context): PushPermission = when {
        NotificationManagerCompat.from(context).areNotificationsEnabled() -> PushPermission.ALLOWED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked(context) -> PushPermission.NOT_ASKED
        else -> PushPermission.REFUSED
    }

    private fun prefs(context: Context) = context.getSharedPreferences("splouch.push", Context.MODE_PRIVATE)

    private fun asked(context: Context): Boolean = prefs(context).getBoolean("asked", false)

    fun markAsked(context: Context) = prefs(context).edit { putBoolean("asked", true) }

    /**
     * `N-07`: registers with FCM — only now, once notifications are allowed — and keeps it
     * registered across launches. The token arrives in [PushService.onRegistered], this
     * time and whenever FCM replaces it.
     */
    fun requestToken(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        messaging.register()
    }

    /** `N-08`: the heat a tap on one of ours asked for, or null for any other Intent. */
    fun focusOf(intent: Intent?): HeatFocus? {
        if (intent?.action != ACTION_OPEN_HEAT) return null
        val meet = intent.getStringExtra(EXTRA_MEET)?.takeIf { it.isNotBlank() } ?: return null
        return HeatFocus(
            meet,
            intent.getStringExtra(EXTRA_EVENT).orEmpty(),
            intent.getStringExtra(EXTRA_HEAT).orEmpty(),
        )
    }

    /**
     * Posts one heat notification. Tagged `<meet>:<event>:<heat>`, so the console's heat
     * replaces that heat's "in 5 minutes" in the shade (`N-06`), as `collapse_key` does in
     * transit.
     */
    fun show(context: Context, data: Map<String, String>) {
        val meet = data["meet_id"].orEmpty()
        val event = data["event"].orEmpty()
        val heat = data["heat"].orEmpty()
        if (meet.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tag = "$meet:$event:$heat"
        val open = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_HEAT)
            .putExtra(EXTRA_MEET, meet)
            .putExtra(EXTRA_EVENT, event)
            .putExtra(EXTRA_HEAT, heat)
        val pending = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = data["body"].orEmpty()
        val channel = if (data["kind"] == "selected") CHANNEL_SELECTED else CHANNEL_UPCOMING
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_heat)
            .setContentTitle(data["title"].orEmpty())
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(tag, 1, notification)
    }
}

/**
 * Firebase's door into the app: this device's token, and the heat notifications themselves.
 *
 * Lint's `MissingFirebaseInstanceTokenRefresh` asks for `onNewToken`, which this Firebase
 * deprecates: since `register()`, a token — the first and every replacement — arrives in
 * [onRegistered], and that is the one this overrides.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PushService : FirebaseMessagingService() {
    override fun onRegistered(token: String) {
        val app = application as SplouchApp
        Handler(Looper.getMainLooper()).post { app.model.setPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Push.show(this, message.data)
    }
}

/** `N-02`, `N-09`: the follows, by server and meet, as one JSON text. */
class PrefsFollowStore(context: Context) : FollowStore {
    private val prefs = context.getSharedPreferences("splouch.follows", Context.MODE_PRIVATE)

    override fun load(): Map<String, MeetFollows> = FollowStore.decode(prefs.getString("all", null))

    override fun save(all: Map<String, MeetFollows>) = prefs.edit {
        if (all.isEmpty()) remove("all") else putString("all", FollowStore.encode(all))
    }
}
