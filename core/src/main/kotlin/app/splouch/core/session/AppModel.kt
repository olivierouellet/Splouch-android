package app.splouch.core.session

import app.splouch.core.follows.FollowRegistration
import app.splouch.core.follows.FollowResult
import app.splouch.core.follows.FollowStore
import app.splouch.core.follows.HeatFocus
import app.splouch.core.follows.InMemoryFollowStore
import app.splouch.core.follows.MeetFollows
import app.splouch.core.follows.PushPermission
import app.splouch.core.schedule.LaneTime
import app.splouch.core.schedule.SuggestionIndex
import app.splouch.core.strings.BuiltInStrings
import app.splouch.core.strings.BundleCache
import app.splouch.core.strings.Labels
import app.splouch.core.strings.StringTable
import app.splouch.core.theme.Theme
import app.splouch.core.transport.SplouchSocket
import app.splouch.core.transport.WebSocketTransport
import app.splouch.core.wire.LocaleEntry
import app.splouch.core.wire.MeetConfig
import app.splouch.core.wire.MeetSummary
import app.splouch.core.wire.PickerConfig
import app.splouch.core.wire.ResultsSnapshot
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ServerInfo
import app.splouch.core.wire.ServerKind
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What [AppModel.removeServer] took away, so [AppModel.restoreServer] can put it back exactly. */
data class RemovedServer(val origin: String, val index: Int, val wasSelected: Boolean)

/** A server the picker's menu can offer (app.md P-11..P-13). */
data class KnownServer(val address: ServerAddress, val name: String, val kind: ServerKind?, val source: Source) {
    /** [CURRENT]: the server in use, listed by none of the others — a Pi picked while a browse ran. */
    enum class Source { DEFAULT, SAVED, DIRECTORY, DISCOVERED, CURRENT }
}

/** P-12: the platform's mDNS browse. Results come back through [AppModel.setDiscovered]. */
interface ServerBrowser {
    fun start()
    fun stop()
}

/** P-12: the server sheet's local-server section — its Search button, the scan, or the scan's answer. */
enum class LocalSearch { IDLE, SEARCHING, DONE }

data class PickerState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val meets: List<MeetSummary> = emptyList(),
    val config: PickerConfig? = null,
    val error: String? = null,
    /**
     * P-17: the app bar is in search mode, and what is typed in it. Held here rather than in
     * the screen so both outlive the picker leaving composition: a meet opened and closed
     * (A-02), or a refresh (P-09), comes back to the same filtered list. A new server starts
     * a fresh [PickerState].
     */
    val searching: Boolean = false,
    val query: String = "",
    /** The reader's language — chosen, else the device's — for country names (P-01, P-17). */
    val lang: String = "",
    /** P-21: the stored filter, mirrored here so the list is one read. */
    val filter: MeetFilter = MeetFilter(),
) {
    /** The search action is offered at all: only once there are enough meets to be worth searching. */
    val canSearch: Boolean get() = MeetSearch.shows(meets.size)

    /**
     * P-21: offered with P-17's search, and whenever a filter is standing, so a list it shrank
     * always shows the control that can widen it again.
     */
    val canFilter: Boolean get() = canSearch || filter.isActive

    /** P-21: what the filter leaves, before any query. */
    val filteredMeets: List<MeetSummary> get() = filter.apply(meets)

    /** P-21: how many meets the filter keeps off the list. */
    val hiddenByFilter: Int get() = meets.size - filteredMeets.size

    /** The app bar is the search field. Not once a refresh has left too few meets to search. */
    val searchOpen: Boolean get() = searching && canSearch

    /**
     * The cards to draw, in the server's order. A query is ignored once the field is gone,
     * or it would hide meets with nothing on screen to say why.
     */
    val shownMeets: List<MeetSummary> get() = filteredMeets.let {
        if (searchOpen) MeetSearch.filter(it, query, lang) else it
    }

    /**
     * P-18: more than [COMPACT_ABOVE] meets → compact rows, no picker image. Counted on
     * every meet the server listed, not the ones a query leaves, so typing never flips the
     * list between the two shapes.
     */
    val compact: Boolean get() = meets.size > COMPACT_ABOVE

    /** P-02: the picker image to fetch for [meet], or null — none supplied, or the list is compact (P-18). */
    fun imageUrl(server: ServerAddress, meet: MeetSummary): String? =
        if (compact || !meet.hasPickerImage) null else server.httpUrl("/picker_image/${MeetContext.enc(meet.id)}")

    /**
     * P-02: hold the image slot on every card when any meet has an image, so the names line
     * up instead of stepping in and out by 56dp. Never in a compact list (P-18).
     */
    val reserveImage: Boolean get() = !compact && meets.any { it.hasPickerImage }

    companion object {
        /** P-18: at this many meets or fewer the picker draws cards; above it, compact rows. */
        const val COMPACT_ABOVE = 10
    }
}

/** One open meet and everything its tabs render from. */
data class MeetState(
    val context: MeetContext,
    val config: MeetConfig,
    val session: MeetSession,
    /** The language in effect: the user's choice, else the meet's (T-06). */
    val lang: String,
    val strings: StringTable,
    val labels: Map<String, String>,
    /**
     * The same table in its short forms — `EV`/`HT`, `ÉP`/`SÉR`, `PR`/`SER`.
     *
     * The board's column headers want the long words (`T-04`), and they have a header row
     * with room for them. The Schedule tab repeats the pair once per card, where the short
     * form says the same thing and leaves the width to the event name beside it; so does
     * the app bar when it takes the board's header row, which is phone-width.
     */
    val shortLabels: Map<String, String>,
    val theme: Theme,
    /** Null until fetched; empty when the meet has no schedule yet (S-07). */
    val schedule: List<ScheduleHeat>? = null,
    /** S-09: built from [schedule], rebuilt with it on every re-fetch (S-21). */
    val suggestions: SuggestionIndex = SuggestionIndex.EMPTY,
    val scheduleError: Boolean = false,
    val refreshing: Boolean = false,
    /** N-02: who this device follows here. Saved per meet; deleted with the meet (N-09). */
    val follows: MeetFollows = MeetFollows(),
    /** N-08: a heat a tapped notification asked the Schedule tab to show, until it has. */
    val focus: HeatFocus? = null,
)

data class UiState(
    val server: ServerAddress,
    val isDefaultServer: Boolean,
    val serverInfo: ServerInfo? = null,
    val checkingServer: Boolean = false,
    val serverError: String? = null,
    /** P-14: set once per handshake; the UI shows it once and calls [AppModel.dismissNotice]. */
    val contractNotice: String? = null,
    val servers: List<KnownServer> = emptyList(),
    val localSearch: LocalSearch = LocalSearch.IDLE,
    val picker: PickerState = PickerState(),
    val meet: MeetState? = null,
    /** A-09: the meet was found gone; the UI says so once and calls [AppModel.dismissMeetGone]. */
    val meetGone: Boolean = false,
    /**
     * A-12: back was asked for and the meet list did not answer, so the meet stays open. The
     * UI says so once (`mobile.picker_unavailable`) and calls [AppModel.dismissPickerUnavailable].
     */
    val pickerUnavailable: Boolean = false,
    val prefs: Preferences = Preferences(),
    /** P-16: a QR code named a server and the reader has not answered yet. */
    val invite: ServerInvite? = null,
    /** Strings for the picker and the server sheet, in the device's or chosen language. */
    val pickerStrings: StringTable = StringTable.EMPTY,
    val locales: List<LocaleEntry> = emptyList(),
    /** C-10: the spectator's setting for [server]. On by default; kept while the server does not count. */
    val counting: Boolean = true,
    /**
     * P-20: the introduction is on screen — due on this install's first picker config, or
     * replayed from settings. Never raised before the server has answered: pages 1 and 4
     * are its words.
     */
    val introOpen: Boolean = false,
    /** N-01: this build can receive pushes at all (Firebase is configured in it). */
    val pushAvailable: Boolean = false,
    /** N-04: whether the spectator lets the app notify. */
    val pushPermission: PushPermission = PushPermission.NOT_ASKED,
    /** N-08: a tapped notification's heat, until its meet is open. */
    val pendingFocus: HeatFocus? = null,
) {
    val kind: ServerKind? get() = serverInfo?.kind

    /**
     * N-01: the bell is offered — a cloud meet whose node says it can reach Firebase
     * (`GET /meet/{id}/config` → `push`), in a build that can receive. Never on a Pi.
     */
    val canNotify: Boolean get() = meet?.let {
        pushAvailable && it.context.kind == ServerKind.CLOUD && it.context.meetId != null && "fcm" in it.config.push
    } ?: false

    /**
     * P-11: the server as the picker names it — only when it is not the app's default. On the
     * default there is nothing to explain; elsewhere a spectator who switched and forgot sees
     * why the meets changed.
     */
    val pickerServerName: String? get() = server.display.takeUnless { isDefaultServer }

    /** P-06's line and full text, or null until this server has sent them. */
    val disclaimer: Disclaimer? get() = disclaimer(picker.config, pickerStrings)

    /** P-07: the Privacy section (and P-20's fourth page) shows only while the server counts. */
    val countingOffered: Boolean get() = countingOffered(picker.config)

    /** P-07: the server's note under the counting toggle. */
    val privacyNote: String? get() = privacyNote(picker.config)
}

/**
 * P-16: a server a QR code named, waiting on the reader's yes. The address is **not**
 * saved, not selected and not even dialled while this sits in [UiState] — a code taped to
 * a wall is a stranger's input, and the only thing it is allowed to do on its own is ask.
 *
 * [address] is null when the link carried nothing usable; then [failure] says why and the
 * prompt is an apology with one button. An invite that came in while the app was already
 * open on a meet leaves the meet alone until the yes.
 */
data class ServerInvite(
    val address: ServerAddress?,
    val standing: Standing = Standing.NEW,
    /** The `GET /server` of `P-13` is in flight; the prompt's button is spinning. */
    val checking: Boolean = false,
    val failure: InviteFailure? = null,
) {
    /** Where the scanned address already stands with the app, which is the whole of what the prompt asks. */
    enum class Standing {
        /** Offered nowhere yet: the prompt asks to **add** it. */
        NEW,

        /** Already in the list, but not the one in use: the prompt asks to **switch**. */
        LISTED,

        /**
         * Already the server in use, and answering. There is nothing to do, so the prompt
         * says so and offers one button — no handshake, which is the point: asking
         * `GET /server` here could only fail, and a poster is scanned on a pool deck where
         * the wifi is worst. Telling a reader the app cannot reach a server it is at that
         * moment showing a live heat from is the one answer that is simply untrue.
         *
         * **Only while it is answering.** A selected server whose handshake failed is
         * [LISTED] instead, so scanning its code re-dials it — that is a spectator whose Pi
         * rebooted, and the useful thing is the reconnect, not a claim that all is well
         * while the screen behind says otherwise.
         */
        IN_USE,
    }
}

/** Why an invite cannot be taken up. The words are the app's own (`T-05`), so this names the case and the UI picks the string. */
enum class InviteFailure { BAD_LINK, CLEARTEXT_NOT_LOCAL, NOT_SPLOUCH, UNREACHABLE }

sealed interface AddServerResult {
    data class Ok(val server: KnownServer) : AddServerResult
    data object InvalidAddress : AddServerResult
    data object CleartextNotLocal : AddServerResult
    data class Unreachable(val reason: String) : AddServerResult
}

/**
 * The app: which server, which meet, and the data every screen renders. Plain Kotlin,
 * driven on a single-threaded [scope]; the platform provides transport, storage, the
 * device language, and the foreground/background/network signals.
 */
class AppModel(
    val defaultServer: ServerAddress,
    private val http: HttpClient,
    private val transport: WebSocketTransport,
    private val vidStore: VidStore,
    private val prefsStore: PreferencesStore,
    private val bundleCache: BundleCache,
    private val scope: CoroutineScope,
    private val deviceLang: String,
    private val timing: SplouchSocket.Timing = SplouchSocket.Timing(),
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val followStore: FollowStore = InMemoryFollowStore(),
) {
    private val _state = MutableStateFlow(UiState(defaultServer, isDefaultServer = true))
    val state: StateFlow<UiState> = _state

    private var api = SplouchApi(http, defaultServer)
    private var directory: List<KnownServer> = emptyList()
    private var discovered: List<KnownServer> = emptyList()
    private var searchLimit: Job? = null

    /** P-12: set by the platform once, after construction — the browse reports back into this model. */
    var serverBrowser: ServerBrowser? = null

    /** N-07: asks the platform for this device's push token; it comes back through [setPushToken]. */
    var requestPushToken: (() -> Unit)? = null
    private var pushToken: String? = null
    private var meetJobs: List<Job> = emptyList()
    private var inForeground = true
    private var generation = 0

    val current: UiState get() = _state.value

    fun start() {
        val prefs = prefsStore.load()
        val server = prefs.server?.let { ServerAddress.parseOrNull(it) } ?: defaultServer
        _state.update {
            it.copy(
                prefs = prefs,
                server = server,
                isDefaultServer = isDefault(server),
                pickerStrings = pickerTable(prefs),
                picker = it.picker.copy(lang = prefs.lang ?: deviceLang, filter = prefs.meetFilter),
                counting = vidStore.counting(server.origin),
            )
        }
        rebuildServers()
        connectServer()
    }

    // ── servers (P-11..P-14) ──────────────────────────────────────────────────

    /** P-11: by normalised origin, so `https://Splouch.org/` is the default too. */
    private fun isDefault(address: ServerAddress): Boolean = address.origin == defaultServer.origin

    fun selectServer(address: ServerAddress) {
        if (address == current.server && current.serverInfo != null) return
        closeMeet()
        savePrefs(current.prefs.copy(server = address.origin.takeIf { !isDefault(address) }))
        _state.update {
            it.copy(
                server = address,
                isDefaultServer = isDefault(address),
                serverInfo = null,
                serverError = null,
                picker = PickerState(lang = readerLang(), filter = current.prefs.meetFilter),
                contractNotice = null,
                counting = vidStore.counting(address.origin),
            )
        }
        directory = emptyList()
        rebuildServers()
        connectServer()
    }

    /** P-13: parse, ask `GET /server`, and only then save and select. */
    suspend fun addServer(text: String): AddServerResult {
        val address = when (val r = ServerAddress.parse(text)) {
            is ServerAddress.Result.Ok -> r.address
            ServerAddress.Result.Invalid -> return AddServerResult.InvalidAddress
            ServerAddress.Result.CleartextNotLocal -> return AddServerResult.CleartextNotLocal
        }
        val info = when (val r = SplouchApi(http, address).serverInfo()) {
            is ApiResult.Ok -> r.value
            ApiResult.NotFound -> return AddServerResult.Unreachable(NOT_SPLOUCH)
            is ApiResult.Failure -> return AddServerResult.Unreachable(r.reason)
        }
        val known = KnownServer(address, info.name.ifEmpty { address.display }, info.kind, KnownServer.Source.SAVED)
        if (!isDefault(address) && address.origin !in current.prefs.servers) {
            savePrefs(current.prefs.copy(servers = current.prefs.servers + address.origin))
        }
        rebuildServers()
        selectServer(address)
        return AddServerResult.Ok(known)
    }

    /**
     * P-13. Returns what it took away, or null if there was nothing to take: removing a
     * hand-added server is a one-finger gesture in the UI, so it has to be undoable, and
     * an undo that cannot put the row back where it was is not one.
     */
    fun removeServer(address: ServerAddress): RemovedServer? {
        val origin = address.origin
        val index = current.prefs.servers.indexOf(origin)
        if (index < 0) return null
        val wasSelected = address == current.server
        savePrefs(current.prefs.copy(servers = current.prefs.servers - origin))
        rebuildServers()
        if (wasSelected) selectServer(defaultServer)
        return RemovedServer(origin, index, wasSelected)
    }

    /**
     * The inverse of [removeServer]: the row goes back at its own index, and back in use
     * if it was in use. No `GET /server` — it answered once when it was added (P-13) and
     * undoing a slip is not the moment to ask again, least of all on a pool deck where
     * the reason it is saved is that the network is unreliable.
     */
    fun restoreServer(removed: RemovedServer) {
        if (removed.origin in current.prefs.servers) return
        val servers = current.prefs.servers.toMutableList()
        servers.add(removed.index.coerceIn(0, servers.size), removed.origin)
        savePrefs(current.prefs.copy(servers = servers))
        rebuildServers()
        if (removed.wasSelected) ServerAddress.parseOrNull(removed.origin)?.let { selectServer(it) }
    }

    // ── added by QR code (P-16) ───────────────────────────────────────────────

    /**
     * P-16: a `https://<default host>/add?server=…` link arrived from the camera. It puts
     * the address on screen as a question and does nothing else — no save, no select, and
     * no request to the address either, since a link that only had to be *scanned* is not
     * consent to dial whatever it names. [acceptInvite] is where `P-13`'s handshake runs.
     *
     * A link that does not parse still raises the prompt, carrying [InviteFailure.BAD_LINK]:
     * a code that opens the app and then appears to do nothing is the one outcome worse
     * than a code that fails, because the reader has no way to tell it from a dead app.
     */
    fun openServerLink(url: String) {
        val invite = when (val r = ServerLink.parse(url, defaultServer.host)) {
            is ServerLink.Result.Ok -> ServerInvite(
                r.address,
                standing = when {
                    r.address == current.server && current.serverInfo != null -> ServerInvite.Standing.IN_USE
                    current.servers.any { it.address == r.address } -> ServerInvite.Standing.LISTED
                    else -> ServerInvite.Standing.NEW
                },
            )
            ServerLink.Result.CleartextNotLocal -> ServerInvite(null, failure = InviteFailure.CLEARTEXT_NOT_LOCAL)
            ServerLink.Result.Invalid -> ServerInvite(null, failure = InviteFailure.BAD_LINK)
        }
        _state.update { it.copy(invite = invite) }
    }

    /**
     * The reader said yes: run `P-13` — `GET /server`, then save, then select — and let the
     * prompt stand while it does, so a Pi that has gone off the network fails *in* the
     * dialog rather than dismissing it and leaving the picker looking untouched.
     */
    fun acceptInvite() {
        val invite = current.invite ?: return
        val address = invite.address ?: return
        // A server already in use has no yes to give: its prompt carries one button, and
        // this guard is what makes that a property of the model rather than of the dialog.
        if (invite.checking || invite.standing == ServerInvite.Standing.IN_USE) return
        _state.update { it.copy(invite = invite.copy(checking = true, failure = null)) }
        scope.launch {
            val failure = when (val r = addServer(address.origin)) {
                is AddServerResult.Ok -> null
                AddServerResult.InvalidAddress -> InviteFailure.BAD_LINK
                AddServerResult.CleartextNotLocal -> InviteFailure.CLEARTEXT_NOT_LOCAL
                is AddServerResult.Unreachable ->
                    if (r.reason == NOT_SPLOUCH) InviteFailure.NOT_SPLOUCH else InviteFailure.UNREACHABLE
            }
            _state.update { s ->
                s.copy(invite = if (failure == null) null else s.invite?.copy(checking = false, failure = failure))
            }
        }
    }

    fun dismissInvite() = _state.update { it.copy(invite = null) }

    /**
     * P-12: the reader tapped Search. Nothing browses until then — not on launch, not on
     * foreground, not on the sheet opening. A scan, not a watch: [LOCAL_SEARCH_LIMIT] and the
     * browse ends whatever it found, the finds staying listed until [stopLocalSearch]. A
     * second tap rescans from empty — the reader may have changed networks.
     */
    fun searchLocal() {
        searchLimit?.cancel()
        serverBrowser?.stop()
        discovered = emptyList()
        rebuildServers()
        _state.update { it.copy(localSearch = LocalSearch.SEARCHING) }
        serverBrowser?.start()
        searchLimit = scope.launch {
            delay(LOCAL_SEARCH_LIMIT)
            searchLimit = null
            serverBrowser?.stop()
            _state.update { it.copy(localSearch = LocalSearch.DONE) }
        }
    }

    /** P-12: the sheet closed or the app left the foreground. What was found goes with it. */
    fun stopLocalSearch() {
        searchLimit?.cancel()
        searchLimit = null
        serverBrowser?.stop()
        discovered = emptyList()
        rebuildServers()
        _state.update { it.copy(localSearch = LocalSearch.IDLE) }
    }

    /** P-12: what the platform's mDNS browse found. A resolve landing after the stop is dropped. */
    fun setDiscovered(servers: List<KnownServer>) {
        if (current.localSearch != LocalSearch.SEARCHING) return
        discovered = servers
        rebuildServers()
    }

    /** The picker's retry after a failed handshake. */
    fun retry() = connectServer()

    fun dismissNotice() = _state.update { it.copy(contractNotice = null) }
    fun dismissMeetGone() = _state.update { it.copy(meetGone = false) }
    fun dismissPickerUnavailable() = _state.update { it.copy(pickerUnavailable = false) }

    private fun rebuildServers() {
        val seen = HashSet<String>()
        val out = ArrayList<KnownServer>()
        fun add(k: KnownServer) {
            if (seen.add(k.address.origin)) out += k
        }
        add(
            KnownServer(
                defaultServer,
                current.serverInfo?.takeIf { current.isDefaultServer }?.name?.ifEmpty { null }
                    ?: defaultServer.display,
                null,
                KnownServer.Source.DEFAULT,
            ),
        )
        current.prefs.servers.mapNotNull {
            ServerAddress.parseOrNull(it)
        }.forEach { add(KnownServer(it, it.display, null, KnownServer.Source.SAVED)) }
        directory.forEach(::add)
        discovered.forEach(::add)
        // P-12: a Pi picked from the browse is in use but saved nowhere; it stays listed once
        // the browse ends, or the sheet would show no server selected.
        add(
            KnownServer(
                current.server,
                current.serverInfo?.name?.ifEmpty { null } ?: current.server.display,
                current.serverInfo?.kind,
                KnownServer.Source.CURRENT,
            ),
        )
        _state.update { it.copy(servers = out) }
    }

    private fun connectServer() {
        val gen = ++generation
        val server = current.server
        api = SplouchApi(http, server)
        _state.update { it.copy(checkingServer = true, serverError = null) }
        scope.launch {
            val r = api.serverInfo()
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(
                            checkingServer = false,
                            serverInfo = r.value,
                            contractNotice = Contract.mismatchNotice(r.value.contract),
                        )
                    }
                    rebuildServers()
                    if (r.value.kind == ServerKind.PI) {
                        openMeet(null)
                    } else {
                        refreshPicker()
                        loadDirectory()
                        // N-08: launched by a tapped notification.
                        current.pendingFocus?.let { openMeet(it.meetId) }
                    }
                    refreshLocales()
                    refreshStrings(server, current.prefs.lang ?: deviceLang, forPicker = true)
                }
                ApiResult.NotFound -> _state.update { it.copy(checkingServer = false, serverError = NOT_SPLOUCH) }
                is ApiResult.Failure -> _state.update { it.copy(checkingServer = false, serverError = r.reason) }
            }
        }
    }

    private fun loadDirectory() {
        val gen = generation
        scope.launch {
            val r = api.servers()
            if (gen != generation || r !is ApiResult.Ok) return@launch
            directory = r.value.mapNotNull { e ->
                ServerAddress.parseOrNull(e.url)?.let { KnownServer(it, e.name, e.kind, KnownServer.Source.DIRECTORY) }
            }
            rebuildServers()
        }
    }

    private fun refreshLocales() {
        val gen = generation
        scope.launch {
            val r = api.locales()
            if (gen == generation && r is ApiResult.Ok) _state.update { it.copy(locales = r.value) }
        }
    }

    // ── picker (P-*) ──────────────────────────────────────────────────────────

    fun refreshPicker() {
        val gen = generation
        if (current.kind != ServerKind.CLOUD) return
        _state.update { it.copy(picker = it.picker.copy(loading = true)) }
        scope.launch {
            val lang = current.prefs.lang ?: deviceLang
            val (meets, config) = coroutineScope {
                val m = async { api.meets() }
                val c = async { api.pickerConfig(lang) }
                m.await() to c.await()
            }
            if (gen != generation) return@launch
            _state.update { s ->
                val ok = meets is ApiResult.Ok
                s.copy(
                    // P-20: due on the first launch whose server answered, postponed by any
                    // launch that did not.
                    introOpen = s.introOpen || (config is ApiResult.Ok && !s.prefs.introSeen),
                    picker = s.picker.copy(
                        loading = false,
                        loaded = s.picker.loaded || ok,
                        meets = (meets as? ApiResult.Ok)?.value ?: s.picker.meets,
                        config = (config as? ApiResult.Ok)?.value ?: s.picker.config,
                        error = when (meets) {
                            is ApiResult.Ok -> null
                            ApiResult.NotFound -> "HTTP 404"
                            is ApiResult.Failure -> meets.reason
                        },
                    ),
                )
            }
        }
    }

    // ── counting (C-10, P-07) and the introduction (P-20) ─────────────────────

    /**
     * C-10's setting for the server in use. Off deletes its `vid` now; on makes a new one at
     * the next `join_meet`, never the old. An open meet's next join reads it again.
     */
    fun setCounting(on: Boolean) {
        vidStore.setCounting(current.server.origin, on)
        _state.update { it.copy(counting = on) }
    }

    /** P-20 from settings About. Only with the server's words in hand, as on first launch. */
    fun replayIntro() {
        if (current.picker.config != null) _state.update { it.copy(introOpen = true) }
    }

    /** P-20 finished or skipped: seen either way, and counting is left as it was. */
    fun finishIntro() {
        if (!current.prefs.introSeen) savePrefs(current.prefs.copy(introSeen = true))
        _state.update { it.copy(introOpen = false) }
    }

    fun openPickerSearch() = _state.update { it.copy(picker = it.picker.copy(searching = true)) }

    fun setPickerQuery(query: String) = _state.update { it.copy(picker = it.picker.copy(query = query)) }

    /** P-21: stored at once — the filter is the spectator's for every server and launch until cleared. */
    fun setMeetFilter(filter: MeetFilter) {
        savePrefs(current.prefs.copy(meetFilter = filter))
        _state.update { it.copy(picker = it.picker.copy(filter = filter)) }
    }

    /** Leaving search mode shows every meet again, so the query goes with it. */
    fun closePickerSearch() = _state.update { it.copy(picker = it.picker.copy(searching = false, query = "")) }

    // ── meet (P-08, A-*, C-08, A-09) ──────────────────────────────────────────

    /**
     * Opens a meet on a cloud, or the one meet on a Pi (`meetId` null). A cloud meet is
     * reached at the `base` its list entry names (C-11); an entry without one, from a server
     * older than app.md v3, at the server URL.
     */
    fun openMeet(meetId: String?) {
        val kind = current.kind ?: return
        val server = current.server
        val base = meetId?.let { id -> current.picker.meets.firstOrNull { it.id == id } }
            ?.let { MeetBase.parse(it.base) } ?: MeetBase.of(server)
        val context = MeetContext(server, kind, meetId, base)
        val gen = generation
        scope.launch {
            when (val r = api.meetConfig(context)) {
                is ApiResult.Ok -> if (gen == generation) startMeet(context, r.value)
                ApiResult.NotFound -> if (gen == generation) {
                    meetId?.let { forgetFollows(server, it) }
                    _state.update { it.copy(meetGone = true, pendingFocus = null) }
                    refreshPicker()
                }
                is ApiResult.Failure -> if (gen == generation) {
                    _state.update { it.copy(picker = it.picker.copy(error = r.reason)) }
                }
            }
        }
    }

    private fun startMeet(listed: MeetContext, config: MeetConfig) {
        closeMeet()
        // A-09: the config names where the meet is now. Opened there from the start, rather
        // than connecting at the listed base only to be moved on.
        val moved = MeetBase.parse(config.base)?.takeIf { listed.kind == ServerKind.CLOUD && it != listed.base }
        val context = moved?.let { listed.copy(base = it) } ?: listed
        val prefs = current.prefs
        val lang = prefs.lang ?: config.settings.locale ?: deviceLang
        val strings = table(context.server, lang)
        val session = MeetSession(context, transport, vidStore, scope, config.settings.numLanes, timing, timeSource)
        _state.update {
            it.copy(
                meet = MeetState(
                    context,
                    config,
                    session,
                    lang,
                    strings,
                    Labels.resolve(config.settings, prefs.lang, prefs.effectiveLabelStyle, strings),
                    Labels.resolve(config.settings, prefs.lang, Labels.SHORT, strings),
                    Theme.from(config.settings),
                    follows = context.meetId?.let { followStore.get(context.server.origin, it) } ?: MeetFollows(),
                    focus = it.pendingFocus?.takeIf { f -> f.meetId == context.meetId },
                ),
                pendingFocus = null,
            )
        }
        session.start()
        if (inForeground) session.startTicker()
        meetJobs = listOf(
            scope.launch { session.reloads.collect { refetchConfig() } },
            scope.launch { session.reconnects.collect { refetchConfig() } },
            scope.launch { session.scheduleUpdates.collect { loadSchedule() } },
            scope.launch { session.resultsFrames.collect { applyConsoleTimes(session, it) } },
            scope.launch { session.moves.collect { onMoved(session) } },
        )
        if (moved != null) refetchConfig() // the config in hand came from the old base
        loadSchedule()
        // N-07: every open re-sends, which heals a node that lost the row.
        if (current.meet?.follows?.isEmpty == false) registerFollows()
        refreshStrings(context.server, lang, forPicker = false)
    }

    /** Closes the meet outright. Back from the meet is [leaveMeet], which asks A-12 first. */
    fun closeMeet() {
        meetJobs.forEach { it.cancel() }
        meetJobs = emptyList()
        current.meet?.session?.close()
        _state.update { it.copy(meet = null) }
    }

    private var leaving: Job? = null

    /**
     * A-02 by a tap: back to the picker (or, on a Pi, to the server list) — once A-12 says
     * the meet list is there to go back to. A second tap while one check is out adds none.
     */
    fun leaveMeet() {
        if (current.meet == null || leaving?.isActive == true) return
        leaving = scope.launch { if (pickerReachable()) closeMeet() }
    }

    /**
     * A-12: is the meet list there to go back to? Asks `GET /meets` with [PICKER_CHECK_TIMEOUT];
     * an answer refreshes the picker's list on the way. No answer raises
     * [UiState.pickerUnavailable] and the meet stays open; the next back asks again, so
     * back works as soon as the list does. A Pi has no list and always says yes.
     *
     * Suspends in the caller, so the system back gesture can start it as the finger moves
     * and abandon it with the gesture: an abandoned check raises nothing.
     */
    suspend fun pickerReachable(): Boolean {
        if (current.kind != ServerKind.CLOUD) return true
        val gen = generation
        val r = withTimeoutOrNull(PICKER_CHECK_TIMEOUT) { api.meets() }
        currentCoroutineContext().ensureActive()
        if (gen != generation) return false
        if (r is ApiResult.Ok) {
            _state.update { it.copy(picker = it.picker.copy(meets = r.value, loaded = true, error = null)) }
            return true
        }
        _state.update { it.copy(pickerUnavailable = true) }
        return false
    }

    /** A-05: pull-to-refresh on the shell — re-fetch config, rejoin the sockets, reload the schedule. */
    fun refreshMeet() {
        val meet = current.meet ?: return
        _state.update { it.copy(meet = it.meet?.copy(refreshing = true)) }
        meet.session.wake()
        loadSchedule()
        refetchConfig(clearRefreshing = true)
    }

    /**
     * The check of A-09 and the redraw of C-08: re-fetch the meet's config. A 404 on the
     * cloud means the meet is gone; any other failure is a network fault and changes nothing.
     */
    private fun refetchConfig(clearRefreshing: Boolean = false) {
        val meet = current.meet ?: return
        val gen = generation
        scope.launch {
            val r = api.meetConfig(meet.session.context)
            if (gen != generation || current.meet?.session !== meet.session) return@launch
            // C-12 by way of A-09: the config came from a base the meet has left.
            if (r is ApiResult.Ok && followBase(meet.session, r.value)) return@launch
            if (r is ApiResult.Ok) meet.session.resize(r.value.settings.numLanes)
            when (r) {
                is ApiResult.Ok -> _state.update { s ->
                    val m = s.meet ?: return@update s
                    val prefs = s.prefs
                    val lang = prefs.lang ?: r.value.settings.locale ?: deviceLang
                    val strings = if (lang == m.lang) m.strings else table(m.context.server, lang)
                    s.copy(
                        meet = m.copy(
                            config = r.value,
                            lang = lang,
                            strings = strings,
                            labels = Labels.resolve(r.value.settings, prefs.lang, prefs.effectiveLabelStyle, strings),
                            shortLabels = Labels.resolve(r.value.settings, prefs.lang, Labels.SHORT, strings),
                            theme = Theme.from(r.value.settings),
                            refreshing = false,
                        ),
                    )
                }
                ApiResult.NotFound -> if (meet.context.kind == ServerKind.CLOUD) {
                    meet.context.meetId?.let { forgetFollows(meet.context.server, it) }
                    closeMeet()
                    _state.update { it.copy(meetGone = true) }
                    refreshPicker()
                } else if (clearRefreshing) {
                    _state.update { it.copy(meet = it.meet?.copy(refreshing = false)) }
                }
                is ApiResult.Failure -> if (clearRefreshing) {
                    _state.update {
                        it.copy(meet = it.meet?.copy(refreshing = false))
                    }
                }
            }
        }
    }

    /**
     * A-09: the config names the meet's `base` now. One that differs from the base in use is
     * followed as a `moved` is (C-12). Returns whether it moved — the config that said so came
     * from the old base, and the one fetched at the new base replaces it.
     */
    private fun followBase(session: MeetSession, config: MeetConfig): Boolean {
        if (session.context.kind != ServerKind.CLOUD) return false
        val base = MeetBase.parse(config.base) ?: return false
        if (base == session.context.base) return false
        session.moveTo(base) // reported back on `moves` → [onMoved]
        return true
    }

    /** C-12: the session's sockets followed the meet to a new base; config and start list follow too. */
    private fun onMoved(session: MeetSession) {
        if (current.meet?.session !== session) return
        _state.update { s -> s.copy(meet = s.meet?.copy(context = session.context)) }
        refetchConfig(clearRefreshing = true)
        loadSchedule()
    }

    /** S-22: a heat's console times, patched into the schedule in hand. */
    private fun applyConsoleTimes(session: MeetSession, snap: ResultsSnapshot) {
        _state.update { s ->
            val m = s.meet?.takeIf { it.session === session } ?: return@update s
            val heats = m.schedule ?: return@update s
            s.copy(meet = m.copy(schedule = LaneTime.applyConsoleTimes(heats, snap)))
        }
    }

    fun loadSchedule() {
        val meet = current.meet ?: return
        val gen = generation
        scope.launch {
            val r = api.schedule(meet.session.context)
            if (gen != generation || current.meet?.session !== meet.session) return@launch
            _state.update { s ->
                val m = s.meet ?: return@update s
                when (r) {
                    is ApiResult.Ok -> s.copy(
                        meet = m.copy(
                            schedule = r.value,
                            suggestions = SuggestionIndex.from(r.value),
                            scheduleError = false,
                        ),
                    )
                    else -> s.copy(meet = m.copy(scheduleError = true))
                }
            }
        }
    }

    // ── language and style (T-05, T-06, T-08, T-09, T-10) ─────────────────────

    fun setLang(lang: String?) {
        savePrefs(current.prefs.copy(lang = lang?.takeIf { it.isNotBlank() }))
        _state.update { it.copy(pickerStrings = pickerTable(it.prefs), picker = it.picker.copy(lang = readerLang())) }
        refreshStrings(current.server, current.prefs.lang ?: deviceLang, forPicker = true)
        if (current.kind == ServerKind.CLOUD) refreshPicker()
        current.meet?.let { m ->
            val newLang = current.prefs.lang ?: m.config.settings.locale ?: deviceLang
            val strings = table(m.context.server, newLang)
            _state.update { s ->
                s.copy(
                    meet = s.meet?.copy(
                        lang = newLang,
                        strings = strings,
                        labels = Labels.resolve(m.config.settings, s.prefs.lang, s.prefs.effectiveLabelStyle, strings),
                        shortLabels = Labels.resolve(m.config.settings, s.prefs.lang, Labels.SHORT, strings),
                    ),
                )
            }
            refreshStrings(m.context.server, newLang, forPicker = false)
            // N-07: the notifications are composed in this language.
            if (!m.follows.isEmpty) registerFollows()
        }
    }

    /**
     * P-15: the app's own light or dark. Device-local and immediate — nothing is re-fetched
     * and no socket is disturbed, the window just redraws in the chosen scheme.
     */
    fun setAppearance(appearance: Appearance) {
        savePrefs(current.prefs.copy(appearance = appearance))
    }

    /** T-09: short or long, nothing else — the choice is the device's, not the meet's. */
    fun setLabelStyle(style: String) {
        savePrefs(current.prefs.copy(labelStyle = if (style == Labels.SHORT) Labels.SHORT else Labels.LONG))
        _state.update { s ->
            s.copy(
                meet = s.meet?.let { m ->
                    m.copy(
                        labels = Labels.resolve(
                            m.config.settings,
                            s.prefs.lang,
                            s.prefs.effectiveLabelStyle,
                            m.strings,
                        ),
                        shortLabels = Labels.resolve(m.config.settings, s.prefs.lang, Labels.SHORT, m.strings),
                    )
                },
            )
        }
    }

    /** A-04: the selected tab survives a relaunch — as a choice, not as a page number (A-11). */
    fun setTab(tab: MeetTab) {
        if (current.prefs.tab != tab) savePrefs(current.prefs.copy(tab = tab))
    }

    private fun pickerTable(prefs: Preferences): StringTable = table(current.server, prefs.lang ?: deviceLang)

    private fun readerLang(): String = current.prefs.lang ?: deviceLang

    /** Cached server value → built-in → built-in English → key (T-10). */
    private fun table(server: ServerAddress, lang: String): StringTable =
        BuiltInStrings.table(lang, bundleCache.read(server.origin, lang)?.bundle)

    /** Revalidate one language in the background and re-derive whatever shows it. */
    private fun refreshStrings(server: ServerAddress, lang: String, forPicker: Boolean) {
        val gen = generation
        scope.launch {
            val etag = bundleCache.read(server.origin, lang)?.etag
            val r = SplouchApi(http, server).i18n(lang, etag)
            if (gen != generation || r !is I18nResult.Ok) return@launch
            bundleCache.write(server.origin, lang, r.text, r.etag)
            _state.update { s ->
                var out = s
                if (forPicker &&
                    (s.prefs.lang ?: deviceLang) == lang
                ) {
                    out = out.copy(pickerStrings = table(server, lang))
                }
                val m = out.meet
                if (m != null && m.lang == lang && m.context.server == server) {
                    val strings = table(server, lang)
                    out = out.copy(
                        meet = m.copy(
                            strings = strings,
                            labels = Labels.resolve(
                                m.config.settings,
                                out.prefs.lang,
                                out.prefs.effectiveLabelStyle,
                                strings,
                            ),
                            shortLabels = Labels.resolve(m.config.settings, out.prefs.lang, Labels.SHORT, strings),
                        ),
                    )
                }
                out
            }
        }
    }

    // ── heat notifications (app.md §10) ───────────────────────────────────────

    /** N-01: set once by the platform — false in a build with no Firebase configuration. */
    fun setPushAvailable(available: Boolean) = _state.update { it.copy(pushAvailable = available) }

    /**
     * N-04: the system's answer, as it stands — it can change in Settings at any time.
     * Allowed asks for the token; what waited on the device is sent once it arrives.
     */
    fun setPushPermission(permission: PushPermission) {
        _state.update { it.copy(pushPermission = permission) }
        if (permission == PushPermission.ALLOWED) {
            if (pushToken == null) requestPushToken?.invoke() else registerFollows()
        }
    }

    /** N-07: Firebase handed over a token. A new one is sent for every meet followed. */
    fun setPushToken(token: String) {
        if (token == pushToken) return
        pushToken = token
        registerAllFollows()
    }

    /** N-02: store the new list for the open meet and send it. */
    fun setFollows(follows: MeetFollows) {
        val meet = current.meet ?: return
        val meetId = meet.context.meetId ?: return
        followStore.set(meet.context.server.origin, meetId, follows)
        _state.update { s -> s.copy(meet = s.meet?.takeIf { it.session === meet.session }?.copy(follows = follows)) }
        registerFollows()
    }

    /**
     * N-07: one `PUT` with every swimmer, at the meet's `base`. Nothing goes while there is
     * no token, and an active list waits for permission (N-04); an empty or paused one (N-11)
     * stops the server whatever the permission. A `409` names another worker: the config there says
     * which, the sockets follow (C-12), and the list is sent again.
     */
    fun registerFollows() {
        val meet = current.meet ?: return
        if (!current.canNotify) return
        val token = pushToken ?: return
        val follows = meet.follows
        if (follows.isActive && current.pushPermission != PushPermission.ALLOWED) return
        val registration = FollowRegistration(token, meet.lang, follows)
        scope.launch {
            var context = meet.session.context
            var r = api.follow(context, registration)
            if (r == FollowResult.MOVED) {
                val config = api.meetConfig(context) as? ApiResult.Ok ?: return@launch
                MeetBase.parse(config.value.base)?.let { context = context.copy(base = it) }
                followBase(meet.session, config.value)
                r = api.follow(context, registration)
            }
            if (r != FollowResult.OK || follows.isEmpty) return@launch
            val base = context.base.url
            if (follows.base != base) {
                val stored = follows.copy(base = base)
                followStore.set(context.server.origin, context.meetId ?: return@launch, stored)
                _state.update { s ->
                    s.copy(meet = s.meet?.takeIf { it.session === meet.session }?.copy(follows = stored))
                }
            }
        }
    }

    /**
     * N-07: a new token goes to every meet with follows, at the `base` each was last
     * registered at, the open one included. A meet that answers 404 is gone, and its follows
     * with it (N-09).
     */
    fun registerAllFollows() {
        val token = pushToken ?: return
        if (current.pushPermission != PushPermission.ALLOWED) return
        val lang = current.meet?.lang ?: readerLang()
        scope.launch {
            for ((key, follows) in followStore.load()) {
                val (origin, meetId) = FollowStore.split(key) ?: continue
                val server = ServerAddress.parseOrNull(origin) ?: continue
                val base = MeetBase.parse(follows.base) ?: MeetBase.of(server)
                val r = SplouchApi(http, server)
                    .follow(
                        MeetContext(server, ServerKind.CLOUD, meetId, base),
                        FollowRegistration(token, lang, follows),
                    )
                if (r == FollowResult.GONE) followStore.set(origin, meetId, null)
            }
        }
    }

    /**
     * N-08: open the notification's meet on its Schedule tab, at its heat. The meet already
     * open is kept; another is closed first. Before the server has answered, the request
     * waits for the handshake ([connectServer]).
     */
    fun openFromNotification(focus: HeatFocus) {
        setTab(MeetTab.SCHEDULE)
        val meet = current.meet
        if (meet != null && meet.context.meetId == focus.meetId) {
            _state.update { s -> s.copy(meet = s.meet?.copy(focus = focus)) }
            return
        }
        _state.update { it.copy(pendingFocus = focus) }
        if (current.kind == ServerKind.CLOUD) {
            closeMeet()
            openMeet(focus.meetId)
        }
    }

    /** N-08: the Schedule tab has shown the heat. */
    fun focusShown() = _state.update { s -> s.copy(meet = s.meet?.copy(focus = null)) }

    /** N-09: the meet is gone, and with it what this device followed there. */
    private fun forgetFollows(server: ServerAddress, meetId: String) = followStore.set(server.origin, meetId, null)

    // ── platform signals ──────────────────────────────────────────────────────

    /** The app came to the foreground: probe the sockets (C-05), re-check the meet (A-09), run the ticker. */
    fun foreground() {
        inForeground = true
        current.meet?.session?.foreground()
        refetchConfig()
    }

    /** The app went to the background: stop the ticker, forget the clock's base (L-12). */
    fun background() {
        inForeground = false
        stopLocalSearch()
        current.meet?.session?.background()
    }

    /** C-05: the network came back. */
    fun networkRestored() {
        current.meet?.session?.wake()
        if (current.serverInfo == null && !current.checkingServer) {
            connectServer()
        } else if (current.picker.error != null && !current.picker.loading) {
            // The handshake held but the meet list did not: that list is what is on screen.
            refreshPicker()
        }
    }

    private fun savePrefs(prefs: Preferences) {
        prefsStore.save(prefs)
        _state.update { it.copy(prefs = prefs) }
    }

    companion object {
        /**
         * The reason an [AddServerResult.Unreachable] carries when the address answered but
         * is not a Splouch server — the one failure the UI words differently from a network
         * fault, so it is a constant rather than a literal matched in three places.
         */
        const val NOT_SPLOUCH = "not a Splouch server"

        /** P-12: how long one scan for the officials' local server browses. */
        val LOCAL_SEARCH_LIMIT = 10.seconds

        /** A-12: how long back waits on the meet list before it keeps the reader on the meet. */
        val PICKER_CHECK_TIMEOUT = 4.seconds
    }
}
