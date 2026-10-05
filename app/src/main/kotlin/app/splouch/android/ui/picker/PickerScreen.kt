package app.splouch.android.ui.picker

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.common.EmptyState
import app.splouch.android.ui.common.reduceMotion
import app.splouch.android.ui.common.rememberFocusReturn
import app.splouch.core.session.AppModel
import app.splouch.core.session.UiState
import app.splouch.core.session.region
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
fun PickerScreen(
    model: AppModel,
    state: UiState,
    images: ImageCache,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    refocusSettings: Boolean,
    onRefocused: () -> Unit,
) {
    val t = state.pickerStrings
    var showDisclaimer by remember { mutableStateOf(false) }
    // X-10: Settings is a destination, so the picker is rebuilt on the way back and the
    // gear asks for focus once it is there; the disclaimer's sheet hands it back to the line.
    val gear = rememberFocusReturn()
    val disclaimerLine = rememberFocusReturn()
    LaunchedEffect(refocusSettings) {
        if (refocusSettings) {
            gear.request()
            onRefocused()
        }
    }
    val cfg = state.picker.config
    val title = (cfg?.title ?: state.serverInfo?.name ?: "").trim()

    // The bar gets out of the way as the list scrolls, which is what a Material app bar
    // does over a long list, and comes back the moment the finger goes the other way.
    val barScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // P-17: search is the app bar's own mode, the way an Android list screen does it — a
    // magnifier among the actions, and the bar becomes the field. Back leaves the mode.
    val picker = state.picker
    val searchLabel = cfg?.strings?.get("meet_search") ?: t.mobile("meet_search")
    // Only a tap on the magnifier raises the keyboard; coming back from a meet to a search
    // left open shows the filtered list without pushing a keyboard over it.
    var focusSearch by remember { mutableStateOf(false) }
    BackHandler(enabled = picker.searchOpen) { model.closePickerSearch() }

    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (picker.searchOpen) {
                SearchAppBar(
                    query = picker.query,
                    label = searchLabel,
                    focus = focusSearch,
                    onChange = model::setPickerQuery,
                    onClose = { model.closePickerSearch() },
                )
            } else {
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
                        if (picker.canSearch) {
                            IconButton(onClick = {
                                focusSearch = true
                                model.openPickerSearch()
                            }) {
                                Icon(painterResource(R.drawable.ic_search), searchLabel)
                            }
                        }
                        // P-19: a gear, not ⋮. Settings hold a toggle, explanatory text and links,
                        // which a `DropdownMenu` renders badly, so they are a destination of their own.
                        IconButton(onClick = onOpenSettings, modifier = Modifier.focusRequester(gear.requester)) {
                            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                        }
                    },
                )
            }
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
                            title = stringResource(
                                if (state.serverError == "not a Splouch server") {
                                    R.string.not_splouch
                                } else {
                                    R.string.server_unreachable
                                },
                            ),
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
                            live = true,
                            image = null,
                            reserveImage = false,
                        ) { model.openMeet(null) }
                    }
                    else -> {
                        // P-06 above the list, over whatever it holds: below it, a season of
                        // meets pushed it out of sight. Nothing until the server has sent it.
                        state.disclaimer?.let { d ->
                            item(key = "disclaimer") {
                                DisclaimerLine(d.short, Modifier.focusRequester(disclaimerLine.requester)) {
                                    showDisclaimer = true
                                }
                            }
                        }
                        val meets = state.picker.meets
                        val shown = state.picker.shownMeets
                        if (meets.isEmpty() && state.picker.loaded) {
                            item {
                                EmptyState(
                                    icon = painterResource(R.drawable.ic_no_events),
                                    title = cfg?.strings?.get("no_meets") ?: t.mobile("no_meets"),
                                )
                            }
                        }
                        // Not `no_meets`: the server has meets, the query hid them.
                        if (meets.isNotEmpty() && shown.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = painterResource(R.drawable.ic_search_off),
                                    title = cfg?.strings?.get("no_meets_match") ?: t.mobile("no_meets_match"),
                                )
                            }
                        }
                        // P-02: the slot is reserved across the list when any meet has an image,
                        // so the names line up instead of stepping in and out by 56dp; taken over
                        // every meet, not the shown ones, so typing does not shift the names.
                        // P-18: past ten meets, compact rows — no image, none fetched, no slot.
                        val unnamed = cfg?.strings?.get("unnamed_meet") ?: t.mobile("unnamed_meet")
                        // P-03: a retained meet says so in words, the server's (`mobile.offline`),
                        // as on iOS — the dot alone is colour, and colour says nothing out loud.
                        val offline = t.mobile("offline")
                        items(shown, key = { it.id }) { m ->
                            // P-01: the organizer's province and country, the country named in the
                            // reader's language.
                            val region = m.region(picker.lang)
                            if (picker.compact) {
                                CompactMeetRow(
                                    name = m.name.ifBlank { unnamed },
                                    meta = listOf(m.meetDate, m.location, region, if (m.offline) offline else "")
                                        .filter { it.isNotBlank() },
                                    live = !m.offline,
                                ) { model.openMeet(m.id) }
                            } else {
                                MeetCard(
                                    name = m.name.ifBlank { unnamed },
                                    meta = listOf(
                                        m.meetDate,
                                        m.location,
                                        region,
                                        m.sport,
                                        if (m.offline) offline else "",
                                    )
                                        .filter { it.isNotBlank() },
                                    live = !m.offline,
                                    image = picker.imageUrl(state.server, m)?.let { url -> remoteBitmap(images, url) },
                                    reserveImage = picker.reserveImage,
                                ) { model.openMeet(m.id) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDisclaimer) {
        state.disclaimer?.let { d ->
            DisclaimerSheet(d.short, d.full) {
                showDisclaimer = false
                disclaimerLine.request()
            }
        }
    }
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
            bmp.asImageBitmap(),
            null,
            Modifier.heightIn(max = 120.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * P-06: one quiet line — hourglass and the server's short text — that nobody can close, so
 * it needs no X, no pill, no stored fold and no focus hand-off. The whole row is the button
 * and at least 48dp tall (X-05); its text is its name to TalkBack, the icon says nothing.
 */
@Composable
private fun DisclaimerLine(short: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .then(modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(R.drawable.ic_hourglass_top), null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        Text(short, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** P-06's tap, Android's way: the server's full text in a `ModalBottomSheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisclaimerSheet(short: String, full: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painterResource(R.drawable.ic_hourglass_top),
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(short, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(full, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun remoteBitmap(images: ImageCache, url: String): Bitmap? {
    val bmp by produceState<Bitmap?>(null, url) { value = images.load(url) }
    return bmp
}

/**
 * P-17's search mode: the app bar holding a back arrow and the field. Every keystroke
 * filters (the list is already on the device, so there is nothing to debounce), and the
 * keyboard's search key only puts the keyboard away. Pinned, not scrolled away with the
 * list, so the query stays in view while the reader looks through what it found.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAppBar(
    query: String,
    label: String,
    focus: Boolean,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focus) focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                placeholder = { Text(label, maxLines = 1) },
                // The bar is the field's container, so the field draws none of its own.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                // Matched folded, so the keyboard has no business capitalising it.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .semantics { contentDescription = label },
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear))
                }
            }
        },
    )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusDot(live)
                    if (image != null) {
                        Image(
                            image.asImageBitmap(),
                            null,
                            Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (reserveImage) {
                        Spacer(Modifier.size(56.dp))
                    }
                }
            },
            headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2) },
            supportingContent = if (meta.isEmpty()) {
                null
            } else {
                (
                    {
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    )
            },
        )
    }
}

/**
 * P-18: one meet in a long list — the dot, the name and one line of details, no image and no
 * slot for one. Still a button of at least 48dp, and one TalkBack stop.
 */
@Composable
private fun CompactMeetRow(name: String, meta: List<String>, live: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(live)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * P-03: a live meet glows; a retained one keeps its place with a dimmed, hollow dot.
 * Silent to TalkBack, as on iOS: a retained meet's word is in the row's details line, in the
 * reader's language, and a live meet is the ordinary case and needs none.
 */
@Composable
private fun StatusDot(live: Boolean) {
    if (!live) {
        Box(
            Modifier.size(10.dp).alpha(0.4f)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
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
            .background(LiveGreen, CircleShape),
    )
}

private val LiveGreen = Color(0xFF4CAF50)
