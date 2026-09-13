package app.splouch.android.ui.picker

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.ui
import app.splouch.core.session.AppModel
import app.splouch.core.session.UiState
import app.splouch.core.wire.MeetSummary
import app.splouch.core.wire.ServerKind

/** The launch screen (app.md §1): the meet list on a cloud, the one meet on a Pi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(model: AppModel, state: UiState, images: ImageCache) {
    val t = state.pickerStrings
    var showServers by remember { mutableStateOf(false) }
    var showPrefs by remember { mutableStateOf(false) }
    val cfg = state.picker.config
    val title = (cfg?.title ?: state.serverInfo?.name ?: "Splouch").ifBlank { " " }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Color(0xFF888888)),
                title = {
                    // P-11: the server stays visible in the header when it is not the default.
                    if (!state.isDefaultServer) Text(state.server.display, fontSize = 13.sp, color = Color(0xFFBBBBBB))
                },
                actions = {
                    IconButton(onClick = { showPrefs = true }) { Icon(painterResource(R.drawable.ic_language), t.ui("language"), tint = Color(0xFF999999)) }
                    IconButton(onClick = { showServers = true }) { Icon(painterResource(R.drawable.ic_server), t.ui("server"), tint = Color(0xFF999999)) }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.picker.loading,
            onRefresh = { model.refreshPicker() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    Branding(title, cfg?.hasLogo == true, cfg?.logoAbove == true, state, images)
                }
                when {
                    state.serverError != null -> item {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (state.serverError == "not a Splouch server") t.ui("not_splouch") else t.ui("server_unreachable"), color = Color(0xFF999999), textAlign = TextAlign.Center)
                            Text(state.server.display, color = Color(0xFF666666), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { model.retry() }) { Text(t.ui("retry")) }
                        }
                    }
                    state.serverInfo == null -> item {
                        Text(t.ui("checking"), color = Color(0xFF555555), modifier = Modifier.padding(top = 60.dp))
                    }
                    state.kind == ServerKind.PI -> item {
                        // §0.2: a Pi has one meet and no picker — the board opens directly; this is
                        // only where A-02's back lands, next to the server menu.
                        MeetCard(name = (state.serverInfo?.name ?: "").ifBlank { state.server.display }, meta = listOf(state.server.display), live = true, image = null) { model.openMeet(null) }
                    }
                    else -> {
                        val meets = state.picker.meets
                        if (meets.isEmpty() && state.picker.loaded) {
                            item { Text(cfg?.strings?.get("no_meets") ?: "", color = Color(0xFF444444), fontSize = 17.sp, modifier = Modifier.padding(top = 60.dp)) }
                        }
                        items(meets, key = { it.id }) { m ->
                            val img = if (m.hasPickerImage) state.server.httpUrl("/picker_image/${m.id}") else null
                            MeetCard(
                                name = m.name.ifBlank { cfg?.strings?.get("unnamed_meet") ?: "" },
                                meta = listOf(m.meetDate, m.location, m.sport).filter { it.isNotBlank() },
                                live = !m.offline,
                                image = img?.let { url -> remoteBitmap(images, url) },
                            ) { model.openMeet(m.id) }
                        }
                        item {
                            Spacer(Modifier.height(28.dp))
                            // P-06: the server's words, never a compiled copy that would need a review to fix.
                            Text(
                                cfg?.strings?.get("results_disclaimer") ?: DISCLAIMER_FLOOR,
                                color = Color(0xFF999999), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).background(Color(0xFF141414), RoundedCornerShape(8.dp)).padding(12.dp, 12.dp),
                            )
                            // P-07: only when this server counts attendance.
                            if (cfg?.analyticsEnabled == true) {
                                Text(cfg.strings["privacy_note"] ?: "", color = Color(0xFF555555), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showServers) ServerSheet(model, state) { showServers = false }
    if (showPrefs) PrefsSheet(model, state) { showPrefs = false }
}

private val Bg = Color(0xFF0D0D0D)

/** The same default the web picker template carries; the server's text replaces it whenever it answers. */
private const val DISCLAIMER_FLOOR = "These are live, unofficial results and are subject to validation. Validated results will be available on the SplashMe app."

@Composable
private fun Branding(title: String, hasLogo: Boolean, logoAbove: Boolean, state: UiState, images: ImageCache) {
    val logo = if (hasLogo) remoteBitmap(images, state.server.httpUrl("/picker_logo")) else null
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (logoAbove) Logo(logo)
        if (title.isNotBlank()) Text(title.uppercase(), color = Color(0xFF888888), fontSize = 20.sp, letterSpacing = 1.5.sp, textAlign = TextAlign.Center)
        if (!logoAbove) Logo(logo)
    }
}

@Composable
private fun Logo(bmp: Bitmap?) {
    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.padding(vertical = 12.dp).heightIn(max = 120.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
}

@Composable
private fun remoteBitmap(images: ImageCache, url: String): Bitmap? {
    val bmp by produceState<Bitmap?>(null, url) { value = images.load(url) }
    return bmp
}

@Composable
private fun MeetCard(name: String, meta: List<String>, live: Boolean, image: Bitmap?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusDot(live)
        if (image != null) Image(image.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(name, color = Color(0xFFE0E0E0), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = Color(0xFF666666), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** P-03: a live meet glows; a retained one keeps its place with a dimmed dot. */
@Composable
private fun StatusDot(live: Boolean) {
    if (!live) {
        Box(Modifier.size(8.dp).border(1.dp, Color(0xFF444444), CircleShape))
        return
    }
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(1f, 0.45f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.size(8.dp).alpha(alpha).background(Color(0xFF4CAF50), CircleShape))
}
