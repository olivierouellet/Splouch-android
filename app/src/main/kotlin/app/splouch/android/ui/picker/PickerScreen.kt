package app.splouch.android.ui.picker

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.splouch.android.ImageCache
import app.splouch.android.R
import app.splouch.android.ui.common.EmptyState
import app.splouch.android.ui.common.reduceMotion
import app.splouch.core.session.AppModel
import app.splouch.core.session.Appearance
import app.splouch.core.session.PickerNotice
import app.splouch.core.session.ShownNotice
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
    var showMenu by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }
    var showPrefs by remember { mutableStateOf(false) }
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
                        // One overflow rather than a row of glyphs. Two of these open a list the
                        // server serves and the third is three fixed choices the app owns, so the
                        // Appearance rows sit inline with a check on the current one — a menu
                        // inside a menu is not something Material does, and the iOS twin's
                        // `Picker(.menu)` reads the same way.
                        IconButton(onClick = { showMenu = true }) {
                            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.server)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_server), null) },
                                onClick = {
                                    showMenu = false
                                    showServers = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.language)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_language), null) },
                                onClick = {
                                    showMenu = false
                                    showPrefs = true
                                },
                            )
                            HorizontalDivider()
                            val closeMenu = { showMenu = false }
                            // A menu item is padded 12dp, not the sheet's 16.
                            SectionHeader(
                                stringResource(R.string.appearance),
                                Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                            )
                            AppearanceChoice(R.string.appearance_dark, Appearance.DARK, state, model, closeMenu)
                            AppearanceChoice(R.string.appearance_light, Appearance.LIGHT, state, model, closeMenu)
                            AppearanceChoice(R.string.appearance_auto, Appearance.AUTO, state, model, closeMenu)
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
                        // P-06/P-07 above the list, over whatever it holds: below it, a season
                        // of meets pushed them out of sight.
                        item(key = "notices") {
                            Notices(
                                state.pickerNotices,
                                state.noticeCollapseLabel,
                                onFold = model::foldNotice,
                                onUnfold = model::unfoldNotice,
                            )
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
                        // P-02: reserve the image slot across the list when any meet has one, so
                        // the names line up instead of stepping in and out by 56dp. Taken over
                        // every meet, not the shown ones, so typing does not shift the names.
                        val anyImage = meets.any { it.hasPickerImage }
                        items(shown, key = { it.id }) { m ->
                            val img = if (m.hasPickerImage) state.server.httpUrl("/picker_image/${m.id}") else null
                            MeetCard(
                                name = m.name.ifBlank { cfg?.strings?.get("unnamed_meet") ?: t.mobile("unnamed_meet") },
                                // P-03: a retained meet says so in words, the server's (`mobile.offline`),
                                // as on iOS — the dot alone is colour, and colour says nothing out loud.
                                meta = listOf(
                                    m.meetDate,
                                    m.location,
                                    m.sport,
                                    if (m.offline) t.mobile("offline") else "",
                                )
                                    .filter { it.isNotBlank() },
                                live = !m.offline,
                                image = img?.let { url -> remoteBitmap(images, url) },
                                reserveImage = anyImage,
                            ) { model.openMeet(m.id) }
                        }
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
            bmp.asImageBitmap(),
            null,
            Modifier.heightIn(max = 120.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * P-06 and P-07. Each shows its full text with an X; the X folds it to a pill and the pill
 * opens it again, so a notice never goes away. One flowing row, in order: an open notice
 * takes the whole line, and folded pills share one, centred — the web picker's layout.
 *
 * Not a dialog and not a consent, so there is no Accept: counting is not the reader's to
 * refuse (C-10), and a button would promise a choice there is none of.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Notices(
    notices: List<ShownNotice>,
    collapseLabel: String,
    onFold: (PickerNotice) -> Unit,
    onUnfold: (PickerNotice) -> Unit,
) {
    // Where focus goes once the notice has redrawn: the pill after a fold, the X after an
    // opening, so a screen reader or a keyboard is not left on a control that has vanished.
    var focusAfter by remember { mutableStateOf<PickerNotice?>(null) }
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (n in notices) {
            key(n.notice) {
                val takeFocus = focusAfter == n.notice
                val focused = { focusAfter = null }
                if (n.folded) {
                    NoticePill(n, takeFocus, focused) {
                        focusAfter = n.notice
                        onUnfold(n.notice)
                    }
                } else {
                    NoticeFull(n, collapseLabel, takeFocus, focused) {
                        focusAfter = n.notice
                        onFold(n.notice)
                    }
                }
            }
        }
    }
}

/**
 * A requester that takes focus once, as the control it is on first appears, when [take]
 * says so. It waits two frames first: the control the reader was on has just left the
 * tree, and TalkBack re-homes its own focus when that happens — a request made in the
 * same frame is overtaken, and TalkBack lands on the first X on the screen.
 */
@Composable
private fun focusOnArrival(take: Boolean, onTaken: () -> Unit): FocusRequester {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (take) {
            withFrameNanos { }
            withFrameNanos { }
            focus.requestFocus()
            onTaken()
        }
    }
    return focus
}

/** An open notice: the server's words in full, and the X that folds them. */
@Composable
private fun NoticeFull(
    notice: ShownNotice,
    collapseLabel: String,
    takeFocus: Boolean,
    onFocused: () -> Unit,
    onFold: () -> Unit,
) {
    val focus = focusOnArrival(takeFocus, onFocused)
    // The disclaimer at full contrast; the privacy note a step quieter, as it always was.
    val disclaimer = notice.notice == PickerNotice.RESULTS_DISCLAIMER
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                notice.text,
                style = if (disclaimer) type.bodyMedium else type.bodySmall,
                color = if (disclaimer) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
            )
            // An IconButton is 48dp, the touch target, and sits in the top end corner
            // however many lines the text wraps to at a large font scale.
            IconButton(onClick = onFold, modifier = Modifier.focusRequester(focus)) {
                Icon(painterResource(R.drawable.ic_close), collapseLabel, Modifier.size(20.dp))
            }
        }
    }
}

/** A folded notice: a chip with its icon and short label. The label is the button's name. */
@Composable
private fun NoticePill(notice: ShownNotice, takeFocus: Boolean, onFocused: () -> Unit, onUnfold: () -> Unit) {
    val focus = focusOnArrival(takeFocus, onFocused)
    // Hourglass: pending validation, not an error. Two people: the visitors being counted.
    // Never a shield, which reads as a privacy setting, and there is none.
    val icon = when (notice.notice) {
        PickerNotice.RESULTS_DISCLAIMER -> R.drawable.ic_hourglass_top
        PickerNotice.PRIVACY_NOTE -> R.drawable.ic_group
    }
    // A small outlined capsule, as iOS's, rather than an AssistChip: the chip's fixed paddings
    // and 14sp label put two French pills past a phone's width, so they stacked. The FlowRow
    // still stacks them when a narrow screen or a large font scale leaves no room. A clickable
    // Surface is a button to TalkBack and pads itself to a 48dp target around its 32dp.
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onUnfold,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.focusRequester(focus),
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(16.dp), tint = colors.primary)
            Text(notice.short, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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

/**
 * P-15, one of the three.
 *
 * `Role.RadioButton` and `selected` rather than a bare check glyph: a tick is a glyph and a
 * glyph says nothing out loud, so without this a listener hears "Dark, Light, Automatic" and
 * cannot tell which one they are already on. Same fix the server and language rows got.
 *
 * **`mergeDescendants = true` is the whole of why it works.** Unmerged, the role and the
 * selected flag sit on a wrapper the reader never lands on, and the node it *does* land on
 * is the bare label — which is what the accessibility tree showed before this: `class=TextView`,
 * `checkable=false`, `selected=false`, on every one of the three.
 */
@Composable
private fun AppearanceChoice(label: Int, value: Appearance, state: UiState, model: AppModel, onPicked: () -> Unit) {
    val selected = state.prefs.appearance == value
    DropdownMenuItem(
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
        },
        text = { Text(stringResource(label)) },
        trailingIcon = { if (selected) Icon(painterResource(R.drawable.ic_check), null) },
        onClick = {
            model.setAppearance(value)
            onPicked()
        },
    )
}
