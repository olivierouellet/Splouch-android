package app.splouch.android.ui.picker

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.common.EmptyState
import app.splouch.android.ui.common.reduceMotion
import app.splouch.core.session.AppModel
import app.splouch.core.session.UiState
import app.splouch.core.wire.ServerKind

/**
 * The launch screen (app.md §1): the meet list on a cloud, the one meet on a Pi.
 *
 * There is no meet open here and so no palette to render — T-01 begins at a meet's
 * `settings` — so every colour on this screen is a Material role and follows the device's
 * light or dark. The web picker's `#0d0d0d` cards and `#2e2e2e` hairlines were chrome,
 * and §0.4 says chrome is the platform's. The one hex left is the live dot, which is
 * product rather than chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(model: AppModel, state: UiState, images: ImageCache, snackbar: SnackbarHostState) {
    val t = state.pickerStrings
    var showServers by remember { mutableStateOf(false) }
    var showPrefs by remember { mutableStateOf(false) }
    val cfg = state.picker.config
    val title = (cfg?.title ?: state.serverInfo?.name ?: "").trim()

    // The bar gets out of the way as the list scrolls, which is what a Material app bar
    // does over a long list, and comes back the moment the finger goes the other way.
    val barScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = barScroll,
                colors = TopAppBarDefaults.topAppBarColors(),
                title = {
                    // P-11 asks for the server in the header when it is not the default. It
                    // is here whatever it is: a bar holding two actions and no title reads
                    // as unfinished, and the operator's own title is already the branding
                    // block below (P-05), so repeating it there would be the one thing
                    // worse than an empty bar.
                    Text(state.server.display, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                },
                actions = {
                    IconButton(onClick = { showPrefs = true }) {
                        Icon(painterResource(R.drawable.ic_language), t.mobile("language"))
                    }
                    IconButton(onClick = { showServers = true }) {
                        Icon(painterResource(R.drawable.ic_server), stringResource(R.string.server))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.picker.loading,
            onRefresh = { model.refreshPicker() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Branding(title, cfg?.hasLogo == true, cfg?.logoAbove == true, state, images) }
                when {
                    state.serverError != null -> item {
                        EmptyState(
                            icon = painterResource(R.drawable.ic_cloud_off),
                            title = stringResource(if (state.serverError == "not a Splouch server") R.string.not_splouch else R.string.server_unreachable),
                            supporting = state.server.display,
                            actionLabel = stringResource(R.string.retry),
                            onAction = { model.retry() },
                        )
                    }
                    state.serverInfo == null -> item {
                        // Waiting on the handshake, not reporting an absence.
                        EmptyState(title = stringResource(R.string.checking), loading = true)
                    }
                    state.kind == ServerKind.PI -> item {
                        // §0.2: a Pi has one meet and no picker — the board opens directly; this is
                        // only where A-02's back lands, next to the server menu.
                        MeetCard(
                            name = (state.serverInfo?.name ?: "").ifBlank { state.server.display },
                            meta = listOf(state.server.display),
                            live = true, image = null, reserveImage = false,
                        ) { model.openMeet(null) }
                    }
                    else -> {
                        val meets = state.picker.meets
                        if (meets.isEmpty() && state.picker.loaded) {
                            item {
                                EmptyState(
                                    icon = painterResource(R.drawable.ic_no_events),
                                    title = cfg?.strings?.get("no_meets") ?: t.mobile("no_meets"),
                                )
                            }
                        }
                        // P-02: reserve the image slot across the list when any meet has one, so
                        // the names line up instead of stepping in and out by 56dp.
                        val anyImage = meets.any { it.hasPickerImage }
                        items(meets, key = { it.id }) { m ->
                            val img = if (m.hasPickerImage) state.server.httpUrl("/picker_image/${m.id}") else null
                            MeetCard(
                                name = m.name.ifBlank { cfg?.strings?.get("unnamed_meet") ?: t.mobile("unnamed_meet") },
                                meta = listOf(m.meetDate, m.location, m.sport).filter { it.isNotBlank() },
                                live = !m.offline,
                                image = img?.let { url -> remoteBitmap(images, url) },
                                reserveImage = anyImage,
                            ) { model.openMeet(m.id) }
                        }
                        item { Footer(cfg?.strings?.get("results_disclaimer") ?: t.mobile("results_disclaimer"),
                            if (cfg?.analyticsEnabled == true) (cfg.strings["privacy_note"] ?: t.mobile("privacy_note")) else null) }
                    }
                }
            }
        }
    }
    if (showServers) ServerSheet(model, state) { showServers = false }
    if (showPrefs) PrefsSheet(model, state) { showPrefs = false }
}

/** P-05: title and logo, either order. An operator who left the title empty gets no empty line. */
@Composable
private fun Branding(title: String, hasLogo: Boolean, logoAbove: Boolean, state: UiState, images: ImageCache) {
    val logo = if (hasLogo) remoteBitmap(images, state.server.httpUrl("/picker_logo")) else null
    if (title.isEmpty() && logo == null) return
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (logoAbove) Logo(logo)
        if (title.isNotEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (!logoAbove) Logo(logo)
    }
}

@Composable
private fun Logo(bmp: Bitmap?) {
    // P-05: operators upload wide banners and square badges alike, so it keeps its own
    // aspect ratio inside the content width under a height cap, never a fixed box.
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), null,
            Modifier.heightIn(max = 120.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * P-06 and P-07. Both were grey fine print trailing off the bottom of the list — the
 * wrong end of the page for the one line standing between a live feed and a spectator
 * treating it as a result. The disclaimer gets a surface of its own and full contrast;
 * the privacy note stays smaller but stays readable.
 */
@Composable
private fun Footer(disclaimer: String, privacy: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                disclaimer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (privacy != null) {
            Text(
                privacy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun remoteBitmap(images: ImageCache, url: String): Bitmap? {
    val bmp by produceState<Bitmap?>(null, url) { value = images.load(url) }
    return bmp
}

/** P-01: one meet, as a card the platform draws — container, ripple, press state and all. */
@Composable
private fun MeetCard(
    name: String,
    meta: List<String>,
    live: Boolean,
    image: Bitmap?,
    reserveImage: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusDot(live)
                    if (image != null) {
                        Image(image.asImageBitmap(), null, Modifier.size(56.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                    } else if (reserveImage) {
                        Spacer(Modifier.size(56.dp))
                    }
                }
            },
            headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2) },
            supportingContent = if (meta.isEmpty()) null else ({
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }),
        )
    }
}

/** P-03: a live meet glows; a retained one keeps its place with a dimmed, hollow dot. */
@Composable
private fun StatusDot(live: Boolean) {
    val label = if (live) "live" else "offline"
    if (!live) {
        Box(
            Modifier.size(10.dp).alpha(0.4f)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                .semantics { contentDescription = label },
        )
        return
    }
    // The dot is the product's own green, not a Material role: "this meet is running now"
    // is the same statement whatever the device's colours are.
    val still = reduceMotion()
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(1f, 0.45f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha")
    Box(
        Modifier.size(10.dp).alpha(if (still) 1f else alpha)
            .background(LiveGreen, CircleShape)
            .semantics { contentDescription = label },
    )
}

private val LiveGreen = Color(0xFF4CAF50)
