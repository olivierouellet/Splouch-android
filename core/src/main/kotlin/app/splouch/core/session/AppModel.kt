package app.splouch.core.session

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
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ServerInfo
import app.splouch.core.wire.ServerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** What [AppModel.removeServer] took away, so [AppModel.restoreServer] can put it back exactly. */
data class RemovedServer(val origin: String, val index: Int, val wasSelected: Boolean)

/** A server the picker's menu can offer (app.md P-11..P-13). */
data class KnownServer(val address: ServerAddress, val name: String, val kind: ServerKind?, val source: Source) {
    enum class Source { DEFAULT, SAVED, DIRECTORY, DISCOVERED }
}

data class PickerState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val meets: List<MeetSummary> = emptyList(),
    val config: PickerConfig? = null,
    val error: String? = null,
)

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
    val picker: PickerState = PickerState(),
    val meet: MeetState? = null,
    /** A-09: the meet was found gone; the UI says so once and calls [AppModel.dismissMeetGone]. */
    val meetGone: Boolean = false,
    val prefs: Preferences = Preferences(),
    /** P-16: a QR code named a server and the reader has not answered yet. */
    val invite: ServerInvite? = null,
    /** Strings for the picker and the server sheet, in the device's or chosen language. */
    val pickerStrings: StringTable = StringTable.EMPTY,
    val locales: List<LocaleEntry> = emptyList(),
) {
    val kind: ServerKind? get() = serverInfo?.kind
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
) {
    private val _state = MutableStateFlow(UiState(defaultServer, isDefaultServer = true))
    val state: StateFlow<UiState> = _state

    private var api = SplouchApi(http, defaultServer)
    private var directory: List<KnownServer> = emptyList()
    private var discovered: List<KnownServer> = emptyList()
    private var meetJobs: List<Job> = emptyList()
    private var inForeground = true
    private var generation = 0

    val current: UiState get() = _state.value

    fun start() {
        val prefs = prefsStore.load()
        val server = prefs.server?.let { ServerAddress.parseOrNull(it) } ?: defaultServer
        _state.update { it.copy(prefs = prefs, server = server, isDefaultServer = server == defaultServer, pickerStrings = pickerTable(prefs)) }
        rebuildServers()
        connectServer()
    }

    // ── servers (P-11..P-14) ──────────────────────────────────────────────────

    fun selectServer(address: ServerAddress) {
        if (address == current.server && current.serverInfo != null) return
        closeMeet()
        savePrefs(current.prefs.copy(server = address.origin.takeIf { address != defaultServer }))
        _state.update { it.copy(server = address, isDefaultServer = address == defaultServer, serverInfo = null, serverError = null, picker = PickerState(), contractNotice = null) }
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
        if (address != defaultServer && address.origin !in current.prefs.servers) {
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
            is ServerLink.Result.Ok -> ServerInvite(r.address, standing = when {
                r.address == current.server && current.serverInfo != null -> ServerInvite.Standing.IN_USE
                current.servers.any { it.address == r.address } -> ServerInvite.Standing.LISTED
                else -> ServerInvite.Standing.NEW
            })
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

    /** P-12: what the platform's mDNS browse found. */
    fun setDiscovered(servers: List<KnownServer>) {
        discovered = servers
        rebuildServers()
    }

    /** The picker's retry after a failed handshake. */
    fun retry() = connectServer()

    fun dismissNotice() = _state.update { it.copy(contractNotice = null) }
    fun dismissMeetGone() = _state.update { it.copy(meetGone = false) }

    private fun rebuildServers() {
        val seen = HashSet<String>()
        val out = ArrayList<KnownServer>()
        fun add(k: KnownServer) { if (seen.add(k.address.origin)) out += k }
        add(KnownServer(defaultServer, current.serverInfo?.takeIf { current.server == defaultServer }?.name?.ifEmpty { null } ?: defaultServer.display, null, KnownServer.Source.DEFAULT))
        current.prefs.servers.mapNotNull { ServerAddress.parseOrNull(it) }.forEach { add(KnownServer(it, it.display, null, KnownServer.Source.SAVED)) }
        directory.forEach(::add)
        discovered.forEach(::add)
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
                    _state.update { it.copy(checkingServer = false, serverInfo = r.value, contractNotice = Contract.mismatchNotice(r.value.contract)) }
                    rebuildServers()
                    if (r.value.kind == ServerKind.PI) {
                        openMeet(null)
                    } else {
                        refreshPicker()
                        loadDirectory()
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
                s.copy(picker = s.picker.copy(
                    loading = false,
                    loaded = s.picker.loaded || ok,
                    meets = (meets as? ApiResult.Ok)?.value ?: s.picker.meets,
                    config = (config as? ApiResult.Ok)?.value ?: s.picker.config,
                    error = when (meets) { is ApiResult.Ok -> null; ApiResult.NotFound -> "HTTP 404"; is ApiResult.Failure -> meets.reason },
                ))
            }
        }
    }

    // ── meet (P-08, A-*, C-08, A-09) ──────────────────────────────────────────

    /** Opens a meet on a cloud, or the one meet on a Pi (`meetId` null). */
    fun openMeet(meetId: String?) {
        val kind = current.kind ?: return
        val context = MeetContext(current.server, kind, meetId)
        val gen = generation
        scope.launch {
            when (val r = api.meetConfig(context)) {
                is ApiResult.Ok -> if (gen == generation) startMeet(context, r.value)
                ApiResult.NotFound -> if (gen == generation) { _state.update { it.copy(meetGone = true) }; refreshPicker() }
                is ApiResult.Failure -> if (gen == generation) _state.update { it.copy(picker = it.picker.copy(error = r.reason)) }
            }
        }
    }

    private fun startMeet(context: MeetContext, config: MeetConfig) {
        closeMeet()
        val prefs = current.prefs
        val lang = prefs.lang ?: config.settings.locale ?: deviceLang
        val strings = table(context.server, lang)
        val session = MeetSession(context, transport, vidStore, scope, config.settings.numLanes, timing, timeSource)
        _state.update {
            it.copy(meet = MeetState(context, config, session, lang, strings,
                Labels.resolve(config.settings, prefs.lang, prefs.effectiveLabelStyle, strings),
                Labels.resolve(config.settings, prefs.lang, Labels.SHORT, strings),
                Theme.from(config.settings)))
        }
        session.start()
        if (inForeground) session.startTicker()
        meetJobs = listOf(
            scope.launch { session.reloads.collect { refetchConfig() } },
            scope.launch { session.reconnects.collect { refetchConfig() } },
            scope.launch { session.scheduleUpdates.collect { loadSchedule() } },
        )
        loadSchedule()
        refreshStrings(context.server, lang, forPicker = false)
    }

    /** A-02: back to the picker (or, on a Pi, to the server list). */
    fun closeMeet() {
        meetJobs.forEach { it.cancel() }
        meetJobs = emptyList()
        current.meet?.session?.close()
        _state.update { it.copy(meet = null) }
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
            val r = api.meetConfig(meet.context)
            if (gen != generation || current.meet?.session !== meet.session) return@launch
            when (r) {
                is ApiResult.Ok -> _state.update { s ->
                    val m = s.meet ?: return@update s
                    val prefs = s.prefs
                    val lang = prefs.lang ?: r.value.settings.locale ?: deviceLang
                    val strings = if (lang == m.lang) m.strings else table(m.context.server, lang)
                    s.copy(meet = m.copy(config = r.value, lang = lang, strings = strings,
                        labels = Labels.resolve(r.value.settings, prefs.lang, prefs.effectiveLabelStyle, strings),
                        shortLabels = Labels.resolve(r.value.settings, prefs.lang, Labels.SHORT, strings),
                        theme = Theme.from(r.value.settings), refreshing = false))
                }
                ApiResult.NotFound -> if (meet.context.kind == ServerKind.CLOUD) {
                    closeMeet()
                    _state.update { it.copy(meetGone = true) }
                    refreshPicker()
                } else if (clearRefreshing) _state.update { it.copy(meet = it.meet?.copy(refreshing = false)) }
                is ApiResult.Failure -> if (clearRefreshing) _state.update { it.copy(meet = it.meet?.copy(refreshing = false)) }
            }
        }
    }

    fun loadSchedule() {
        val meet = current.meet ?: return
        val gen = generation
        scope.launch {
            val r = api.schedule(meet.context)
            if (gen != generation || current.meet?.session !== meet.session) return@launch
            _state.update { s ->
                val m = s.meet ?: return@update s
                when (r) {
                    is ApiResult.Ok -> s.copy(meet = m.copy(
                        schedule = r.value, suggestions = SuggestionIndex.from(r.value), scheduleError = false))
                    else -> s.copy(meet = m.copy(scheduleError = true))
                }
            }
        }
    }

    // ── language and style (T-05, T-06, T-08, T-09, T-10) ─────────────────────

    fun setLang(lang: String?) {
        savePrefs(current.prefs.copy(lang = lang?.takeIf { it.isNotBlank() }))
        _state.update { it.copy(pickerStrings = pickerTable(it.prefs)) }
        refreshStrings(current.server, current.prefs.lang ?: deviceLang, forPicker = true)
        if (current.kind == ServerKind.CLOUD) refreshPicker()
        current.meet?.let { m ->
            val newLang = current.prefs.lang ?: m.config.settings.locale ?: deviceLang
            val strings = table(m.context.server, newLang)
            _state.update { s -> s.copy(meet = s.meet?.copy(lang = newLang, strings = strings,
                labels = Labels.resolve(m.config.settings, s.prefs.lang, s.prefs.effectiveLabelStyle, strings),
                shortLabels = Labels.resolve(m.config.settings, s.prefs.lang, Labels.SHORT, strings))) }
            refreshStrings(m.context.server, newLang, forPicker = false)
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
        _state.update { s -> s.copy(meet = s.meet?.let { m -> m.copy(
            labels = Labels.resolve(m.config.settings, s.prefs.lang, s.prefs.effectiveLabelStyle, m.strings),
            shortLabels = Labels.resolve(m.config.settings, s.prefs.lang, Labels.SHORT, m.strings)) }) }
    }

    /** A-04: the selected tab survives a relaunch — as a choice, not as a page number (A-11). */
    fun setTab(tab: MeetTab) {
        if (current.prefs.tab != tab) savePrefs(current.prefs.copy(tab = tab))
    }

    private fun pickerTable(prefs: Preferences): StringTable = table(current.server, prefs.lang ?: deviceLang)

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
                if (forPicker && (s.prefs.lang ?: deviceLang) == lang) out = out.copy(pickerStrings = table(server, lang))
                val m = out.meet
                if (m != null && m.lang == lang && m.context.server == server) {
                    val strings = table(server, lang)
                    out = out.copy(meet = m.copy(strings = strings,
                        labels = Labels.resolve(m.config.settings, out.prefs.lang, out.prefs.effectiveLabelStyle, strings),
                        shortLabels = Labels.resolve(m.config.settings, out.prefs.lang, Labels.SHORT, strings)))
                }
                out
            }
        }
    }

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
        current.meet?.session?.background()
    }

    /** C-05: the network came back. */
    fun networkRestored() {
        current.meet?.session?.wake()
        if (current.serverInfo == null && !current.checkingServer) connectServer()
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
    }
}
