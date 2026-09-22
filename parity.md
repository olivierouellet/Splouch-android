# Splouch for Android — parity ledger

One row per feature ID in [`app.md`](../Splouch/docs/app.md) **v1**, the behaviour contract.
That document owns *what* each feature is and whether it is required; this file owns
*whether it is implemented here, and why not* (`app.md` §0.1).

**Stack:** Jetpack Compose, Kotlin

Status is one of `done` / `deferred` / `diverges` / `n/a — <reason>`. `diverges` is
built and working, but deliberately not what the contract says — the note carries what
the app does instead, why, and that `app.md` still needs the matching edit. IDs are the
join key across the three repos and are never renumbered — a row that goes away keeps
its ID and gains a note. Rows already marked `n/a` below are the ones the contract itself puts out of
scope for a native client; everything else starts `deferred`.

`Level` is copied from `app.md` v1 for triage only. **`app.md` is authoritative** —
if the two ever disagree, that document wins and this one is stale.

**Verification status (2026-09-22).** `./gradlew :core:test` passes (95 unit tests over
the socket loop, race clock, frame merge, results grid, strings, filters, the app model,
the seed column, the two palettes, `A-11`'s console gate, `L-23`'s lap count, `P-16`'s
QR link and the string-snapshot coverage check) and `./gradlew :app:assembleDebug` builds.

**`P-16` was seen running on 2026-09-22** — the prompt, the handshake, the switch, the
already-here case and three ways of failing, on the Medium Phone AVD against a local
cloud and a local Pi; its row carries what was on screen. **One thing in it cannot be checked from here:** whether
the stock camera opens the app rather than a chooser is Android's own link verification,
and that needs `https://splouch.ca/.well-known/assetlinks.json` to exist and to name the
release signing certificate. It does not exist yet, so on the AVD the Intent was delivered
by `adb` — which is the same Intent, minus the OS's decision to send it. `adb shell pm
get-app-links app.splouch.android` is where that decision shows: `1024` today, `verified`
once the file is served.

**`L-23` was seen running on 2026-09-18**, on the Medium Phone AVD against a local Pi
playing `200m_medley_2heats.cts` in a venue with touchpads at one end only — both
directions, the final-stretch colour, the handover at the finish and a `reset`. Its row
carries what was on screen.

**`A-11` was seen running this day**, on the Medium Phone AVD against a local Pi, with the
console switched underneath a running app from the Pi's own Settings → Timing page — never
by restarting the app, and never by editing a fixture:

- `cts_gen6` (`timed: true`): three tabs, and Results on "Waiting for results…" — the
  falsehood the row exists to remove.
- Switched to **Manual — no timing console** while the reader stood on Results: the tab
  went, the bar re-laid itself out to two real destinations, and the reader was moved to
  the Scoreboard. One swipe from there landed on Schedule, so the pager is two pages and
  not three with one blank.
- Switched **back** to `cts_gen7` while the reader stood on Schedule: Results returned as
  the middle tab and the reader **stayed on Schedule**, the selected pill following it from
  index 1 to index 2. This is the case an `Int` would have failed, and the reason `A-04`
  now stores an identity.
- The navigation rail (`A-07`, landscape) carries the two destinations the bar does.
- The `Int` migration: a device whose stored `tab` was the old `2` opened straight onto
  Schedule in a two-tab meet — read through the order it was written in, not applied as an
  index — and the key was rewritten as `<string>SCOREBOARD</string>` on the first change
  after that.

Not seen on a device, and covered by unit tests only: the **absent** `console` field, since
that needs a server too old to send it. The tolerant-parse defaults around it are unit-tested.

**The iOS UI pass was ported this day** — the schedule's seed column and short heat
labels, `P-15`'s Appearance preference in place of the meet's palette, the lane-row sizes,
and twelve lanes in portrait. Seen working on a Medium Phone AVD (API 37, a 411×914dp
Pixel-8 viewport) against a local Pi playing `200m_medley_2heats.cts` and a purpose-built
stub for the two cases no recording covers:

- 6, 8 and 12 lanes, portrait and landscape. Twelve fit one screen with rows equal, the
  table filling to the bottom and the last lane clear of the navigation bar — on the
  Pixel-8 viewport and again at 360×780dp, where the row type lands on its 13dp floor.
- Landscape drops the column titles at twelve lanes and brings them back at six, so the
  decision is not sticking.
- Dark, Light and Automatic, Automatic following `cmd uimode night`. Light **holds inside a
  meet**, which is the whole point of `P-15`; the running time and the `L-11` flash are
  legible on it where the old fixed grey and white were not.
- Default and 2.0× text. The board holds its layout and the chrome grows, and at 2.0× the
  lanes take the app bar's header row (`L-15` rung a) with short labels and no wall clock.
- A heat of relays with alt names and one without: the alt line drops before any type
  shrinks, and comes back when there is room.
- A full twelve-lane heat run start to finish, lanes locking one at a time. One frame
  caught lane 8 mid-`L-11` flash — its time drawn in `row_text` on its way to the timing
  colour — while lanes 9-12 were still mid-`L-12` pulse and lanes 1-7 had settled, which
  is the three states of a lane in one screenshot.
- A start list whose every seed time is `NT`: the column reserves two characters.
- A meet sending `theme_colors` (`bg #123456`): ignored, per the `T-01` departure.

Two bugs only a device could show, both fixed and recorded in §8 **Font scale**: Material's
inherited `24.sp` `lineHeight` overflowing rows that had been measured at a pinned type
size, and `AutoSizeText`'s `8.sp` floor inverting under a `dp`-derived ceiling.

The UI was rebuilt on the platform's own components on 2026-09-15 — see **The native-UI
pass** below. Seen working on a Medium Phone AVD (API 37) against a local Pi playing
`200m_medley_2heats.cts` and a local cloud relaying it: the picker as a Material list with dynamic colour, its
branding and its disclaimer block; the language and server sheets; the scoreboard with names, clubs and the app bar's
back arrow; results with times, deltas and places; the schedule with the current heat
marked and the filter button in the app bar; the filter sheet's field, keyboard and
stand-down; landscape on both board tabs, where the header folds into the app bar and the
tabs move to a navigation rail; `R-01`'s waiting line replacing the grid, reached by
turning airplane mode on under a live meet; the tab choice surviving a relaunch.

Not yet exercised on a device: a language change made from the preference
sheet mid-meet, the mDNS browse, `A-09`, and a **tablet or foldable** — the navigation rail
was seen only at phone-landscape width, never at `Medium`. (A light board
used to be on this list because no operator ships one; `P-15` made it the reader's choice,
so it is reachable from the picker's menu on any meet.) **Not yet heard:** none of the accessibility work in
§8 has been run under TalkBack. The labels and merge points are in place and compile;
whether a lane *reads* sensibly is a judgement only the screen reader settles.

Suggested order: `P-13` (handshake) → `C-01`–`C-05` (sockets) → `P-01`/`P-08` (meet
list, open a meet) → `L-01`–`L-14` (scoreboard). Results, Schedule and the `T-*`
language controls reuse all of it.


## The native-UI pass (2026-09-15)

The screens were rebuilt on the platform's own components. One line decides everything
below, and it is the one `Splouch-ios` drew first: **the chrome is the platform's, the
board is the meet's.**

`app.md` §7 opens "every meet themes itself; the app does not have a look of its own",
and that stays true where it was ever about a meet. But the *picker* is not a meet — it
spans meets and `T-01` begins at a meet's `settings`, so there was never a palette to
render there. What the app was painting instead was `picker.html`'s stylesheet: `#0d0d0d`
behind the list, `#1a1a1a` cards inside a `#2e2e2e` hairline, five greys for text. That is
the web mechanism, not the behaviour (§0.4), and the behaviour is "a list of meets you can
tap". So:

| | Draws with | Follows |
| --- | --- | --- |
| Picker, server and language sheets, errors | Material 3 roles, dynamic colour where the device offers it | the **reader** (`P-15`) |
| Scoreboard, Results, Schedule cards | one of two palettes, and `T-03`'s faces from the meet | the **reader** for colour, the **meet** for the faces |
| Sheets and dialogs *inside* a meet | Material components over a scheme derived from the palette in effect | the same one answer |

**Amended by `P-15` (see `T-01`).** The line above still holds for the *faces* — `T-03` is
untouched, and a meet still draws in the three it names. The palette is no longer the
meet's: the reader picks Dark, Light or Automatic and that holds everywhere. What the two
tables in this row used to say — the picker pinned dark "for now", the board following its
own `bg` luminance — was the problem `P-15` fixes, and both are gone. The "for now" was
waiting on a light board to match; there is one now, and the reader chooses it rather than
the operator.

`T-03` and `T-07` are unchanged. `T-01` and `T-02` are a deliberate departure with its cost
named in their rows, and `app.md` still needs the matching edit.

Also in this pass, and listed here because no single ID owns them:

- **Type is the M3 scale, not the stylesheet's pixels.** The schedule's swimmer names were
  14sp — four points under every other app's body text, on the screen a spectator reads
  hardest. They are `bodyLarge` now, with lane numbers and clubs at `bodyMedium` and the
  heat heading at `titleMedium`, so the heading is no longer smaller than the rows it heads.
- **One band of chrome, not two.** `BoardHeader` no longer draws its own background and
  hairline. That band was `mobile.html`'s `border-bottom`, holding two documents together
  as one screen (§0.4); under a real app bar it was simply a second strip.
- **Portrait lanes share the board.** They were a fixed floor under a scroll, so a six-lane
  meet drew six short stripes and left the rest of the screen bare. They now split the
  height with 44dp as the floor rather than the size, and the row's type scales with the
  height it got.
- **The window follows the device.** `themes.xml` was `Theme.DeviceDefault.NoActionBar`
  with `windowBackground #000000`; it is `DayNight` now, and the system bars' icon polarity
  is set per screen (`SplouchRoot.SystemBarAppearance`) rather than pinned to light.
- **The only hex left outside the board** is the picker's live dot, `#4CAF50`. That is
  product — "this meet is running now" is the same statement whatever the device's colours
  are — rather than chrome.


## 1. Meet picker

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `P-01` | List of meets as cards: name, date, location, sport | must | `done` | `PickerScreen`: a Material `Card` per meet with a `ListItem` inside — container, ripple and press state all drawn by the platform, where they were a hand-rolled `Row` over `#1a1a1a` inside a `#2e2e2e` border. **No disclosure chevron**: Android list rows do not carry one, that is an iOS affordance, and the card's own ripple already says the row opens something |
| `P-02` | Per-meet picker image on the card, when the meet supplies one | should | `done` | `ImageCache` fetches `/picker_image/{id}` when `has_picker_image`. The 56dp slot is reserved across the whole list when *any* meet has an image, so the names line up instead of stepping in and out by 56dp from row to row |
| `P-03` | Offline meets stay listed, marked with a dimmed status dot | must | `done` | dimmed `onSurfaceVariant` dot for `offline`, pulsing `#4CAF50` for live; both carry a spoken label, since a dot says nothing out loud. The pulse honours Remove animations (`ui/common/Motion.kt`) — it is decoration the dot's own colour already states |
| `P-04` | Empty state when no meets are active | must | `done` | the shared `EmptyState` titled `strings.no_meets` from `/picker/config`. The server's words are the title and nothing is invented to sit under them (T-05) — but a glyph is not a translation, so it carries one (`event_busy`), because a single line floating in a blank screen reads as a failure to load rather than as an answer |
| `P-05` | Picker branding: title, logo, logo above or below the title | should | `done` | title (`headlineSmall`, a heading to the screen reader), `/picker_logo`, `logo_above`. A title the operator leaves empty is not drawn at all rather than holding an empty line, and the block is skipped entirely when there is neither title nor logo |
| `P-06` | Unofficial-results disclaimer under the list | must | `done` | server text from `/picker/config`, falling back to the same key in the `GET /i18n/{lang}` snapshot; never a compiled copy. It sits in a `surfaceContainerHigh` block at `bodyMedium` and full `onSurface` contrast — it was 12sp `#999999` on `#141414`, fine print trailing off the bottom, which for the one line standing between a live feed and a spectator treating it as a result is the wrong end of the page |
| `P-07` | Privacy note, shown whenever attendance counting is on for this server | must | `done` | gated on `analytics_enabled`; `bodySmall` and `onSurfaceVariant` — still fine print, but readable by someone who goes looking for it, where it was 12sp `#555555` on near-black |
| `P-08` | Selecting a meet opens the app shell for it | must | `done` | `AppModel.openMeet` → `GET /meet/{id}/config` → `MeetSession` |
| `P-09` | Pull-to-refresh re-fetches the meet list | should | `done` | Material `PullToRefreshBox` |
| `P-10` | Install hand-off: store links to the native iOS/Android apps once they ship, Add-to-Home-Screen until then | web-only | `n/a` | web-only — an app satisfies it by existing (app.md §0.3) |
| `P-11` | Choose which server to connect to, from a list, in the picker's menu | native-only | `done` | `ServerSheet`: default + saved + `GET /servers` + nearby, as `ListItem` rows made `selectable` so the current choice reaches TalkBack — the radio button is a glyph and a glyph says nothing out loud. The server is in the picker's app bar **always**, not only when it is not the default — that is the row's floor, and a bar holding two actions and no title reads as unfinished while the operator's own title is already the branding block below. In a meet it is the app bar's subtitle in portrait and sits beside the clock in the landscape bar header |
| `P-12` | Servers on the local network are offered without anyone typing an address | native-only | `done` | `NsdBrowser` on `_splouch._tcp`, dialled by `.local` host name (option A); the platform exposes the host name from Android 14 only, so older devices see nothing and add the Pi by hand |
| `P-13` | A server can be added by hand, checked before it is saved | native-only | `done` | `AppModel.addServer`: parse, `GET /server`, then save; `http` accepted for `.local` names only. One row, not three: the section header already said what this is, so the field submits itself — the IME's Go key, or the tick that appears once there is something to send — and its `supportingText` carries the progress and the error. Taking one back out is a swipe (`SwipeToDismissBox`), the gesture Android uses for removing a row, where it was a "Remove" text button in the trailing slot. **Only hand-added servers swipe**: the default and the ones `GET /servers` lists cannot be removed, and a gesture that sometimes does nothing is worse than no gesture. It is **undoable**, which a one-finger gesture over a typed address has to be: `AppModel.removeServer` hands back the origin, its index and whether it was in use, `restoreServer` puts all three back, and the snackbar carrying the Undo is hosted by the sheet rather than by the app — the sheet is its own window and a snackbar from underneath would be drawn behind it. No second `GET /server` on the way back: it answered when the server was added, and undoing a slip is not the moment to ask again on a network that is unreliable enough to be why the address is saved. Tests: AppModelTests |
| `P-15` | The app's own light/dark, chosen in the picker's menu: Dark, Light, Automatic | native-only | `done` | `Appearance` on `Preferences`, set through `AppModel.setAppearance`, resolved to one boolean in **one place** — `SplouchRoot`. The choice holds everywhere: picker, board, and the chrome over both. `BoardTheme` and `SplouchTheme` are *handed* the answer rather than deciding, so the bars, the rows and the sheets cannot disagree, and the meet's own `theme_colors` are not consulted (see `T-01`). `AUTO` asks `isSystemInDarkTheme()`, which is what lets it move with the time of day. **Dark is the default**, including for preferences stored before the key existed — the app has been pinned dark since the web page it came from, and a spectator who never opens this menu should see the app they saw yesterday. `themes.xml` paints the launch window before Compose exists and cannot read a preference, so it carries the default and `MainActivity` repaints it from the stored choice; without that a Light reader got a black flash on every cold start. The control is a real overflow menu, which also took in the Server and Language buttons that were loose glyphs in the bar: two of them open a list the server serves, and Appearance is three fixed choices the app owns, so its rows sit inline with a check on the current one and `Role.RadioButton` so the choice is announced. The words are the app's (`T-05`) — light and dark are about the device, and no server serves them. Tests: `the two palettes are the server's, not the meet's`, `appearance defaults to dark and round-trips` |
| `P-16` | A server can be added by scanning a QR code: the code opens the app, the app asks, and the server is added and selected on a yes. Without the app installed the same code lands on a web page offering the store | native-only | `done` | **New ID, claimed here.** `app.md` has no `P-16` yet and needs the row; this is the same position `P-15` is in, and the note below is what the contract text should say. **The code carries `https://splouch.ca/add?server=<origin>`, and the shape is forced.** A `splouch://` scheme is opened by no stock camera — several scanners refuse a private scheme outright — and, more to the point, it has no answer for the case the poster is printed for, which is the spectator who *does not have the app yet*. An `https` URL has one for free: with the app installed the App Link intercepts it, and without the app nothing intercepts it at all, so the browser lands on the page and the page offers the store (`P-10`'s hand-off, which is web-only and stays that way). The host is the app's own default server — the one URL the app ships knowing (`P-11`) — because an App Link is verified per host and the app cannot verify a pool's Pi, which has no `https` and no certificate. So the Pi travels in the query and never in the authority, and `ServerLink.parse` takes the host from `AppModel.defaultServer`: **a server cannot mint a code that adds a different server.** The address inside is held to exactly what a typed one is (`P-13`): `ServerAddress.parse`, so `http` only for a `.local` name or the developer loopbacks, and then `GET /server` before anything is saved. A printed code is a stranger's input in a way a typed address is not, so the floor could not be lower here. **The prompt is the whole of the consent, so it is a dialog and it names the address.** `ServerInviteDialog` over whatever is on screen — the reader was looking at a poster a moment ago and often the app was not running, so the question is the screen's only business until it is answered. It shows `address.display`, host and port: the one thing being agreed to is which machine the app will talk to, and "Add this server?" without a name is not a question. Scanning **does nothing else at all** — no save, no select, and no request to the address either, since scanning a code is not consent to dial whatever it names. `AppModel.acceptInvite` is where `P-13`'s handshake runs, and it leaves the dialog standing while it does: a Pi that has gone off the network fails *in* the dialog, retryable, rather than dismissing it and leaving the picker looking untouched. **The prompt asks only what is left to ask** (`ServerInvite.Standing`): a server offered nowhere yet asks to **add**, one already in the list but not in use asks to **switch**, and one that is already the server in use and answering says *You're already on this server* over one button and asks the network **nothing**. That last case matters more than it looks: routing it through the handshake would have meant a scan on a deck with bad wifi answering "cannot reach this server" over a live heat coming from that very server, which is the one reply that is simply untrue. `acceptInvite` refuses it too, so the single button is a property of the model and not of the dialog. **Only while it is answering**, though — a selected server whose handshake failed asks to switch, because that is a spectator whose Pi rebooted and the useful thing is the reconnect, not a claim that all is well while the screen behind says otherwise. **A link that does not parse still raises the prompt**, carrying `InviteFailure.BAD_LINK`. A code that opens the app and then appears to do nothing is the one outcome worse than a code that fails, because the reader cannot tell it from a dead app. `MainActivity` is `singleTask` so a scan made while the app is already open reaches `onNewIntent` instead of stacking a second copy over a running meet, and every Intent is **consumed** — `data` nulled, the debug `server` extra removed — because an Activity hands back its starting Intent on every recreation and a link left in place would add the server again after a process death. The invite lives on `AppModel`, so the question survives a recreation while the reader is still reading it. **The web half is not in this repo and the feature is inert without it** — `splouch.ca` must serve two things: • `/.well-known/assetlinks.json`, `Content-Type: application/json`, no redirect, naming `app.splouch.android` and the **release** signing certificate's SHA-256 (`keytool -list -v -keystore <release.jks> -alias <alias>`; Play App Signing means the fingerprint to publish is the one on the Play Console's *App signing* page, not the upload key's). Until it is served, `adb shell pm get-app-links app.splouch.android` reports `splouch.ca: 1024` — no response — and Android offers a chooser instead of opening the app. It verifies at install time and is cached, so a pool with no internet is unaffected once the app is installed. • `GET /add?server=<origin>`, an HTML page for the case the app is absent: the store link, and nothing that pretends to be the app. It is the only page whose absence a spectator would meet as a 404 after scanning. A third belongs to the Pi: rendering the code itself, somewhere an operator can print it — carrying **the cloud that Pi publishes to, never the Pi's own address** (see the amendment below). **Seen working on 2026-09-22** (Medium Phone AVD, API 37, against a local cloud on `10.0.2.2:5055` and a local Pi on `10.0.2.2:5056`), driven with `adb shell am start -a android.intent.action.VIEW -d '<link>' app.splouch.android`, which delivers the Intent the verified link would and exercises everything but the OS's own resolution: the prompt over the picker naming `10.0.2.2:5056`; Add saving it, selecting it, and — a Pi having one meet — opening the board straight onto it; the same code scanned again *inside* that meet, drawn in the board's own Material roles with the heat still running behind it — **switch** while a different server was selected, and *You're already on this server* with a single OK once it was the one in use, dismissing to an untouched board; `http://192.168.1.10:5000` refused as "Cannot add this server" with one button; and `gone.local:5000` accepted at the prompt and failing on the handshake, the dialog holding open with the address, the reason and a retry, the meet behind untouched. Tests: `a QR link parses only on the app's own host, and only as an address`, `a QR link asks before it adds, and the yes runs the handshake`, `a link that fails says so in the prompt rather than closing it`. **Amended 2026-09-22 in `app.md` and the `Splouch` repo — nothing changed here.** The first draft had the Pi mint its own `.local` origin into the query, which reads as obviously right — the Pi *is* the better server, no internet dependency and an unthrottled race clock (`P-11`) — and is wrong for a **poster**. A `.local` name resolves only for a phone already joined to the venue's wifi: a spectator on cellular gets a handshake failure, a guest network with client isolation blocks mDNS even for one that did join, and a printed code cannot ask which network it is being read on. So a minted code names a **cloud**. The second reason is this app's own: a cloud session's launch screen is the meet list, so the reader lands on the **picker** — which is where `P-06`'s unofficial-results disclaimer lives, and a spectator arriving by camera is exactly the one who has never seen it. A Pi session skips the picker (`app.md` §0.2), so the first draft would have walked a first-time reader straight past a `must`. The Pi is still the better server and is still offered — by `P-12`'s browse, *after* the picker, to a phone that has by then joined the right network. **No app change was needed and none was made**: the link shape is byte-identical, `ServerLink.parse` still accepts a `.local` address (a hand-made code and `P-13`'s typed one are unchanged), and `connectServer` already routes a cloud origin to `refreshPicker()` instead of `openMeet(null)`. The constraint is on what a server **mints**, never on what this client accepts. The `Seen working` log above predates the amendment and stands as the record of what was actually run. |

## 2. App shell

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `A-01` | Three tabs — Scoreboard, Results, Schedule — each with icon and label | must | `done` | a Material `NavigationBar`, or a `NavigationRail` where the window is wide (A-07); Tabler icons ported as vectors, labels from `mobile.*`. Both take their selected pill and accent from the meet's palette through the derived scheme, so the board's timing colour marks the tab you are on. **Three for a timed meet, two for a meet with no timing console** — `A-11` is the exception to the count, and both controls are built by iterating `MeetTab.of(config)`, so the untimed meet's bar genuinely holds two destinations rather than three with one hidden |
| `A-02` | Back affordance to the meet picker | must | `done` | the navigation icon of a Material `TopAppBar`, plus the system back gesture — answered by `PredictiveBackHandler`, so the board eases back and fades with the finger, settles if the gesture completes and springs back if it is abandoned (`enableOnBackInvokedCallback` in the manifest is what turns that on at all). Opening and closing a meet slide along the shared axis rather than cutting between frames. It was a fixed-width `IconButton` wedged into the *bottom* `NavigationBar`, which is not a place Android puts back — a bottom bar holds peer destinations, and a fourth thing in it reads as a fourth tab however it is sized. Its content description is still `mobile.back_to_meets` |
| `A-03` | A horizontal swipe on the tab's content moves between adjacent tabs | must | `done` | `HorizontalPager`, full width, over `MeetTab.of(config).size` pages — two where `A-11` has taken Results away, and the drag tracks exactly as it does over three. On Android the pager **is** the Material idiom for peer sections, so the one control answers this row and `A-10` together — which is what app.md's revised §0.4 and `A-03` note now say, and why `Splouch-ios` records the same ID as `diverges` without this being a regression against it |
| `A-04` | The selected tab survives a relaunch | should | `done` | `prefs.tab` via `AppModel.setTab`. **It was an `Int` and is now a `MeetTab`** — the row says a choice survives, and an index is not one once `A-11` can change what sits at each position: page 2 is Schedule in a timed meet and does not exist in an untimed one. A device that stored the old index is read through the tab order it was written in, once, and written back as a name (`PrefsPreferencesStore.storedTab`), so an upgrade does not move anyone |
| `A-05` | Pull-to-refresh re-fetches config and rejoins the sockets | should | `done` | `PullToRefreshBox` → `refreshMeet`: config re-fetch, socket probe, schedule reload |
| `A-06` | Content clears notch, Dynamic Island, and home indicator | must (free natively) | `done` | edge-to-edge with `Scaffold` insets |
| `A-07` | Portrait stacks label under icon; landscape drops labels to save height | should | `diverges` | **Width decides, and the labels stay.** `Compact` width (phone portrait) keeps the bottom `NavigationBar` with its labels; `Medium` and `Expanded` — phone landscape, tablets, an unfolded foldable — move the tabs to a `NavigationRail` down the side, labels intact (`ui/Adaptive.kt`, Material's own `WindowWidthSizeClass`). This serves what the row is *for* — landscape has height to spare nowhere and a bottom bar spends it — better than dropping the labels does, and unlike the orientation branch it has an answer for a tablet. `app.md` A-07 needs the matching edit: the requirement is that the tabs stop costing height on a short window, not that the labels go |
| `A-08` | Window and home-screen title is the meet's `app_window_title`, falling back to its `name` | web-only | `n/a` | web-only — an app satisfies it by existing (app.md §0.3) |
| `A-09` | Meet goes offline mid-session → return to the picker | must | `done` | config re-fetched on reconnect, foreground, pull-to-refresh and `reload`; a 404 on a cloud closes the meet (`AppModelTests`) |
| `A-10` | The movement is visible: the tabs follow the finger through the drag and settle on release | should | `done` | the same `HorizontalPager` as `A-03` — on Android one control gives both rows, which is the case app.md's note describes as the platform offering the better answer. Unchanged by `A-11`: the page count is the tab count, and `beyondViewportPageCount` follows it, so the two-tab meet keeps both pages composed the way the three-tab one keeps all three |
| `A-11` | A meet run with **no timing console** has no Results tab at all — not an empty one | must | `done` | `MeetTab.of(config)` is the only place that decides, from `settings.console.timed` — `ConsoleInfo` parses the block from `settings` on the cloud and from the top level of the Pi's `/config`, which the existing `MeetSettings.fromJson` already reaches for both. **`timed`, never `key == "manual"`**: the server reads it off the decoder, so a local-plugin console driven by hand is covered and a key comparison would have called it timed. Absent, null, malformed or a type nobody promised all read as `timed: true` — a server too old to send `console` is a server with a console, and a parse failure is not a reason to take a screen away. `key` is carried for a support question and nothing branches on it. It re-evaluates on every config fetch the app already makes — reconnect, foreground, `A-05`'s pull-to-refresh and `C-08`'s `reload` — because the operator can switch consoles mid-meet in either direction, so the tab comes and goes live, with no restart and without disturbing the session or its three sockets. Nothing else is conditional: the Scoreboard is what the operator is driving from `/manual`, the Schedule is the full start list either way, and there is no "no results this meet" screen — the tab goes, the remaining two are unchanged. Tests: `A-11 reads console timed, defaults to timed, and never branches on the key`, `A-11 the Results tab comes and goes with the console, on the re-fetch the app already makes`, `A-04 stores a tab as a choice, not as a number` |

## 3.1 Header

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-01` | EVENT number, HEAT number, each a small label above a large value | must | `done` | `BoardHeader` in portrait — the word over the number, with room between them and no background band of its own. Its type is a fraction of the height the header has, the way `L-15`'s rows take theirs from the height they share, and font-scale independent (see §8). In landscape the row moves into the app bar (`BoardBarHeader`), where a bar is one line high so the word sits *beside* its number; the bar was otherwise a back arrow in the corner and nothing next to it. The word and its number are one thing to the screen reader, and say nothing at all before a number arrives rather than stopping it on a blank |
| `L-02` | Event name | must | `done` | `event_name`, composed from parts when a language is chosen (T-11). Centred in the slot between the header cells and the clock — pinned left between two items sitting at the edges, it read as floating rather than placed — and the one label on the board allowed two lines: it has a header to itself, so it uses the second before it starts shrinking, where a lane's name shares its row with five other cells and has to hold its line |
| `L-03` | Wall clock, `HH:MM`, ticking every second | must | `done` | `wallClock()`, device time, ticks each second; trailing item of the header in both layouts, and of the app bar row in landscape |

## 3.2 Lane table

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-04` | One row per lane, `num_lanes` rows always present | must | `done` | `ScoreboardState(numLanes)` |
| `L-05` | Columns: lane · name (+ alt sub-line) · club · time · delta · place | must | `done` | `BoardGrid` |
| `L-06` | Relay member names on a dimmed second line under the name | must | `done` | `lane_name_alt<i>` dimmed under the name. **Suppressed in a tight portrait board** (`PortraitRow.showsAlt`): it is the first rung of `L-15`'s degradation ladder, because it is the one line on the row that is not a swimmer, a time or a place, and a team name set at 9dp to keep it helps nobody. It comes back the moment the lanes have the height for it — the threshold is a *measured* relay row, not a guess |
| `L-07` | Column visibility follows config: `show_name`, `show_club`, `show_delta`, `show_position` | must | `done` | `show_*` from settings |
| `L-08` | Column *headers* hide independently of the columns: `show_*_header` | should | `done` | `show_*_header`, landscape header row |
| `L-09` | Empty lanes render blank in place — rows never collapse or shift | must | `done` | rows are positional; blanks stay in place. Portrait rows now *share* the board's height the way the landscape table already did, with 44dp as the floor rather than the size: fixed at the floor, a six-lane meet drew six short stripes and left the rest of the screen bare, which is a table sized to its content rather than a board filling its board |
| `L-10` | Frames are partial: merge changed keys into local state, never replace | must | `done` | `ScoreboardState.apply` merges keys (`ScoreboardStateTests`) |
| `L-11` | A running lane's time is styled distinctly; on stop it plays a one-shot "locked" transition, cancelled if the lane starts running… | must | `done` | grey while running, white→time flash on the stop edge keyed by `lockEdge`, cancelled when running again |
| `L-12` | Every running lane's time cell shows the race clock: one value for the heat, re-based by the server every couple of seconds and… | must | `done` | `RaceClock` over a monotonic mark, 10Hz ticker in `MeetSession`, frozen forward at 3 sync intervals, pulse fallback, stopped on background (`RaceClockTests`, `ScoreboardStateTests`) |
| `L-13` | Event or heat change blanks all times, deltas, and places | must | `done` | baseline on connect, running-hold, otherwise blank (`ScoreboardStateTests`) |
| `L-14` | Returning to the tab re-runs layout and refreshes the clock | must (native: on-appear) | `done` | `revealScoreboard` on page settle |
| `L-23` | While a lane is swimming the **delta cell** carries that lane's lengths, centred, in the header's accent colour; the delta takes the cell back at the finish, in its better/worse colour. The column header never changes | should | `done` | `ScoreboardState.View.lap(lane, settings)` decides the tenant and `BoardGrid`'s `LapText` draws it; `LapDirection`/`LapSettings`/`LapCount` in `core/board/LapCount.kt`, `show_laps` and `lap_direction` on `MeetSettings`, and `lane_splits<i>`, `expected_splits` and `split_step` merged in `apply`. **The rule is a pure function of merged state and reads no edge at all** — a phone that joins mid-heat is handed the cached snapshot (api.md §3) with every transition already behind it, so anything keyed to "when X changed" would read that replay as a heat in which nothing happened. The place ends the lap, not the delta: a swimmer with no seed time never gets a delta, and waiting for one would leave the lap sitting under a finished swim for the rest of the heat. The final stretch is `splits + split_step >= expected_splits`, **never `+ 1`**: with touchpads at one end only the count arrives 2, 4, 6 and never lands on an odd length, so `+ 1` would never fire on exactly the pool where the deck can least easily tell; the countdown reads 8, 6, 4, 2 there and the final-stretch colour covers the last two lengths. `expected_splits` floors at 0 and `split_step` at 1 on decode — a step of 0 would fire the test a length early. No animation on either the handover or the final stretch, per the note in `app.md`. The `Δ` goes from the landscape header while `show_laps` is on and stays gone through the results — but **only on this tab**: the Results tab is all finishes, so `BoardGrid`'s `laps` parameter defaults off there and `R-04`'s header keeps its word. The screen reader speaks the lap as `R.string.laps` plus the number: the wire has no spectator word for it (`GET /i18n/{lang}`'s `labels` names the six columns and stops), so this one string is the app's and ships in the app's three languages rather than the meet's — the single place on the board where `T-04` does not hold, taken over reading a bare integer after the time. **A reset clears it** (see the `reset` note below). **Two departures.** The colours are the four the contract names — `header_label`, `time`, `delta_better`, `delta_worse` — but taken from the reader's palette rather than the meet's `theme_colors`, which is this app's standing `T-01`/`T-02` divergence (`P-15`) and not particular to `L-23`. And "centred in the cell" is landscape's alone, where the cell is a column: a portrait row has no delta column — its second line is time · delta · place laid out in flow (`L-15`) — so there the cell is sized to what it holds and only the colour and the swap carry the meaning. **That line was re-grouped for this feature** and the change outlives it: the time and whatever shares its line now sit *together* at the left of the line, with the slack between that pair and the place rather than between the time and the delta. A delta pushed against the place column read as belonging to the place, and a bare lap there read as a rank — which is the confusion `L-23` gives up a column of its own to avoid. The time is the only weighted cell of the pair, so it still cannot squeeze what follows it off the row. The Results tab shares the row and takes the same grouping. **One thing the palette costs it.** The lap is drawn in `header_label`, and this repo's dark palette has that at `#ffffff` where the server's `DEFAULT_THEME_COLORS` has `#3b9eff` — a transcription slip `Theme.kt` already documents, not something `L-23` introduced. Against `row_text: #e0e0e0` it leaves the lap almost the row's own colour, so on the dark board the handover to the delta's green/grey carries the signal and the accent adds little; the final-stretch gold is unaffected and lands clearly. Left as it is deliberately — changing it would recolour EVENT/HEAT and the place column across every board. **Seen on screen, both directions** (Medium Phone AVD, API 37, local Pi playing `200m_medley_2heats.cts`, `show_laps` on): a 200 m in a 25 m pool with `touchpad_sides: 1`, so the venue sends `expected_splits: 8` and `split_step: 2` — the half-padded case, and the one worth drawing. Counting **up**: nothing under the start list at the top of the heat, `2` at 55.2s, then `6` in the timing gold at 1:51.2 — the final stretch firing at `+ split_step` on a pool where `+ 1` never would, since the count goes 2, 4, 6 and never lands on 7. At the finish the deltas took every cell back and the places ended the lap in the six lanes whose delta never came, with nothing left behind. Counting **down**: `8` in every lane with a swimmer at the top of heat 2, before anyone had touched a wall, then `6`, and `2` in gold — it reads 8, 6, 4, 2 here and never reaches 1. The direction was switched from the Pi's Settings → Display **under a running app**, and `C-08`'s `reload` carried it without a restart. Landscape read `LN · NAME · CLUB · TIME · ⟨blank⟩ · PL`, `DIFF` gone and the other titles untouched, while the Results tab in the same orientation kept its `DIFF`. A `reset` — the Pi's own, from stopping a test session — wiped names, times and the lap together and left eight blank lanes in place. Held at 360×780dp and 2.0× text. Tests: `ScoreboardStateTests` (counting up waits for the first wall; counting down shows the full distance before anyone has swum; an empty lane in a short heat shows nothing either way; the delta reclaims the cell at the finish and a place does too with no delta ever coming; `expected_splits: 0` falls back to counting up and has no final stretch; the final stretch fires at `+ split_step` and not `+ 1`; a mid-heat join reads the replay alone; a heat change empties the cell; a reconnect and a reset both drop the venue numbers), `PayloadTests` (`show_laps` off by default, anything but `down` counts up, tolerant decode of the three wire fields) and `SessionTests` (the `reset` frame) |

## 3.3 Layout

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-15` | Portrait: two-line compact row — lane number spanning left, name on line 1 with club right-aligned, time and delta and place on… | must | `done` | **Line two groups the time with the cell beside it** — time · delta (or `L-23`'s lap) as one pair at the left, the place hard right, and the slack between the pair and the place instead of inside it. The delta used to sit against the place column, where it read as an annotation on the rank rather than on the time it is measured against. `PortraitGrid`, and the row's type is now taken from the height the row actually got (26% of it, clamped 13–24sp) rather than from the screen's dimensions alone — a six-lane meet is read across a pool rather than set at the size a sixteen-lane one needs. **Club, delta and place read at the name's size** rather than a quarter under it: they were sized as annotations on a row whose only real content was the name and the time, but on a results board the club and the place are half of what a spectator is there for. Colour still carries the hierarchy — the club stays `th_text` against the name's `row_text` — so matching the sizes does not make them compete. The lanes have no divider between them: the `row_odd`/`row_even` stripe already separates them, and the hairline was the web table's. **Twelve lanes now fit on one screen.** The 44dp row floor is gone — it was what stopped them, not the type: twelve rows wanted 528dp against the ~510 a phone has, and the miss was the floor. Rows share the whole scroll height, stay equal, and the last one is **not** special-cased; `Scaffold` has already taken the system bars off that height, so it clears the navigation bar on its own. Degradation is in order of what a spectator loses least, each rung only on need: **(a)** the app bar takes the EVENT / HEAT row the way landscape's does — worth a whole header band, but it costs the meet's title and `P-11`'s server name, so ten lanes and under keep them; the bar's copy takes the short labels (`MeetState.shortLabels`) and drops the wall clock, since it is phone-width there and the status bar is showing the time a few points above it. **(b)** the relay name goes (`L-06`) before any type shrinks. **(c)** the type scales to the share each row got (`PortraitRow.scale`, floored at 0.72; past that the board scrolls, which is the honest answer — twelve lanes of relay at 8dp would fit and be unreadable). Everything in the row scales together, the spacing and padding included, so the hierarchy holds at any count. **Nothing in any of that is a constant** (`BoardMetrics`). A lane's natural height is a real row laid out at full size and never drawn — two of them, one with a relay name — inside a `Box` already pinned to the height `BoxWithConstraints` handed it, so measuring cannot move what it measures. The header band is measured while it is drawn and kept after it moves to the bar, which is exactly the number needed to decide whether to move it back. The system bars are `Scaffold`'s and never a subtracted guess. And the "does this board need the bar?" decision is normalised to the height the lanes would have **with** the row in the bar — the same number either way the answer comes out — so it cannot oscillate by moving the header, gaining the height, deciding it was not needed and moving it back. The iOS twin shipped four device-specific constants first and they were wrong on every phone but the one they were measured on |
| `L-16` | Landscape: full table with a header row, row font scaled to lane count | should | `done` | `LandscapeGrid`, row font from lane count. **The row font takes 55% of a row's height, not 48.** A landscape row has no vertical padding at all, so that share was the whole of what held the type down, and a six-lane board set its numbers at 26 in 55dp rows. **Time, delta and place are set from the row font like the name** (0.85, and the place takes it whole) rather than two thirds of it — they are the numbers a spectator came to read and they were the smallest things on the row. The delta's column widened to 110dp to match and shrinks rather than wraps (`AutoSizeText` down to 0.6 of its size): the column is fixed and a four-lane board at the 32dp cap can ask for more width than it has, and a delta on two lines is not a delta. **The column titles are the first thing dropped when the rows get tight** — sized once with their band withheld, and if that comes out under 14dp they go and the rows are sized again over the whole height. The band is *measured* while it is drawn and kept after it goes, so the decision reads a cached number and not the height it changes; it cannot oscillate, and dropping the header only ever makes the type bigger. The place cell is centred here, under its own centred column header — it was the only cell not lining up with the word above it. Portrait keeps it hard right (`L-15`): no header there, and it is the last thing on the line |
| `L-17` | Long names shrink to fit their cell, ellipsis only as a floor | must | `done` | `AutoSizeText`: `BasicText` with `TextAutoSize.StepBased`, ellipsis only below 8sp. The platform measures, so there is no per-frame re-fit to gate (app.md L-17's note) |

## 3.4 Not on this tab

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-18` | Carousel / fullscreen image overlay | n/a | `n/a` | images are local to the Pi and are never relayed |
| `L-19` | Podium highlight animation | n/a | `n/a` | Pi-local, `race_finished` is not forwarded |
| `L-20` | Animated column show/hide, operator-driven | n/a | `n/a` | cloud columns are always visible |
| `L-21` | Any timed hold on a state — the kiosk's 3s `brief_results` flash, its results pause, its leave-results debounce | n/a | `n/a` | still real on the kiosk, still deliberately absent here: the phone shows the last frame received and runs no… |
| `L-22` | Independent per-lane clock, each lane timing its own length | n/a | `n/a` | the console has one race clock and the lanes mirror it; a lane's own figure exists only as its split… |

## 4. Results tab

The whole tab is absent for a meet with no timing console (`A-11`); everything below
describes it where it exists. Nothing in this section is otherwise conditional — in
particular `R-01`'s waiting line is still the right answer for a timed meet that has not
yet sent a snapshot, and it is precisely because it is *not* the right answer for a meet
that will never send one that `A-11` takes the tab away instead of rewording it.

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `R-01` | Until the first snapshot: "Waiting for results…" in place of the table, not a table of blank rows | must | `done` | the shared `EmptyState` titled `mobile.waiting_results` **instead of** the grid, in both orientations. This used to be an empty grid with the line under it in portrait and nothing at all in landscape; app.md was revised to the present wording while `Splouch-ios` was built, and this row now matches it. Seen on screen by turning airplane mode on under a live meet, which is `R-02`'s wipe returning the tab to exactly this state |
| `R-02` | A disconnect, or `meet_live` going false, wipes the board and returns it to that state | must | `done` | `ResultsBoard.clear` on disconnect and `meet_live` false (`SessionTests`) |
| `R-03` | Header shows the snapshot's own event, heat, and event name | must | `done` |  |
| `R-04` | Same six columns and visibility flags as the Scoreboard tab | must | `done` | same `BoardGrid` and settings |
| `R-05` | Lane sort: row index = `channel`; a lane with no final time leaves its row blank | must | `done` | row = `channel`; absent `sort` is lane (`ResultsAndDeltaTests`) |
| `R-06` | Place sort: rows fill top-down as a ranking | must | `done` |  |
| `R-07` | A missing time renders as `—`, not blank; a missing place renders empty — no dash, and no `#` in front of it | should | `done` |  |
| `R-08` | Long names shrink to fit rather than clipping | should | `done` | same `AutoSizeText` |
| `R-09` | Final times carry the "locked" styling | should | `done` |  |
| `R-10` | Returning to the tab re-joins the meet, reconnecting first if needed | must | `done` | `revealResults`: re-join if connected, else probe |

## 5.1 The list

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `S-01` | Every heat as a card: scheduled time, "Event N — Heat M", event name | must | `done` | `ScheduleTab` from `/meet/{id}/schedule` or `/schedule.json`, all three on one line. Sizes follow the Material type scale rather than the web stylesheet's pixels: names `bodyLarge`, lane numbers and clubs `bodyMedium`, the heat heading `titleMedium` semibold. It was 14/12/12sp under a 13sp heading — so the heading was smaller than the rows it headed, and the names, on the screen a spectator reads hardest, were four points under every other app's body text. `sp` throughout, so all of it follows the device's font-size setting. All three sit on one line: the heat identifier leading, the event name beside it, the scheduled time trailing at **its own natural width**, so a card reads as two runs rather than three — what the heat is on the left, when it swims on the right. It is deliberately *not* in the lanes' seed-time column (`S-02`): a scheduled time is a clock time, not a seed time, and sharing that ruler left a narrow `9:12` stranded a ruler's width from the event name and made a heat with no scheduled time reserve the full width for nothing. When `time` is empty nothing is drawn for it at all and the event name runs to the edge of the card. app.md `S-01` asks only that the card carry the time, the identifier and the name — the alignment between them is this app's layout choice, not the contract's. The two halves of the identifier are separated by a **doubled space** rather than an em dash: the dash was punctuation between two things that are not a range or a pair, cost the event name four characters and said nothing the gap does not, and twice the within-pair gap is what groups `EV 12` against `HT 3` in a monospaced face. The identifier takes the **short** labels (`MeetState.shortLabels` — `EV`/`HT`, `ÉP`/`SÉR`, `PR`/`SER`): the pair repeats once per card and the long words buy nothing the numbers beside them do not already say, while the width they cost is the event name's. The board's own column headers keep the long forms (`T-04`). Past a `fontScale` of 1.8 the heading reflows instead of shrinking — identifier and event name take the full width and wrap, and the scheduled time drops to a line of its own, still trailing and still at its own width — an unscheduled heat gets no such line rather than a blank row of ruler height — so `EV 12` / `HT 3` breaks at a space rather than down a narrow gutter. Unlike the iOS twin there was no 12→14 to make here: the seed time and the club beside it were already one size (`bodyMedium`), and no face scales itself, so nothing was squaring the setting — see §8 **Font scale** |
| `S-02` | Each card lists its lanes: lane number, name, club, seed time | must | `done` | The seed time has a **column**: as wide as the widest seed time currently on screen, with the value right-aligned in it (`TimingCell`). Without it the club's position followed the width of the time next to it and the codes zig-zagged down the card — `NT` is six characters narrower than `1:04.219`, but `57.40` against `1:04.219` was already enough to break the column on an ordinary heat. Sized from a hidden copy of the longest string rather than a constant, so a meet whose every seed time is `NT` reserves two characters and not eight, and computed **once over the whole visible list** (`ScheduleFilter.widestSeedTime`) rather than per card — a spectator scrolling past a hundred heats reads the times as one column, and a width that changed card to card would undo that. The ruler is drawn transparent rather than skipped, because it has to measure, and `clearAndSetSemantics` keeps it out of the accessibility tree: TalkBack reading every row's column width before its time would be worse than the misalignment it fixes. Never wrapped — a seed time broken across two lines reads as two times. Tests: `the widest seed time sizes the column…` |
| `S-03` | Relay entries show member first names joined by `·` | should | `done` | `ScheduleFilter.displayName` |
| `S-04` | Alternating card backgrounds, computed over *visible* cards so filtering keeps the stripe | should | `done` | stripe over visible cards |
| `S-05` | The heat the meet is on is highlighted in the list | must | `done` | `MeetSession.currentHeat` off both sockets, strings compared; marked by a 4dp bar in the meet's timing colour down the card's leading edge rather than a 2dp box drawn around it — the list scrolls to this card, so it has to read at a glance without redrawing the card's own edges. The card is `IntrinsicSize.Min` so the bar has a height to fill: inside a lazy list the height constraint is unbounded, and `fillMaxHeight` against that is zero |
| `S-06` | The list auto-scrolls to the current heat once per appearance | must | `done` | once per appearance, re-armed on resume; heat headings carry `heading()` so TalkBack's rotor can jump heat to heat instead of walking every lane — on the screen whose whole purpose is finding one swimmer among several hundred |
| `S-07` | Empty state when no meet file is loaded | must | `done` | the shared `EmptyState` titled `mobile.no_schedule`. A start list that would not load is a network fault rather than an empty meet and says so instead (`server_unreachable`, a native string) |

## 5.2 Filtering

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `S-08` | Full-screen filter sheet, opened from a button in the top bar | must | `done` | `FilterSheet`, a full-screen dialog with its own `TopAppBar` — a close on the leading edge and the confirming tick opposite, which is the shape Material gives a full-screen dialog; with only the tick, the back gesture was the one way out for anyone who did not reach for it. The button that opens it is now genuinely *in the top bar*, and only on the tab it filters; the Schedule tab used to draw a 56dp header strip of its own under the shell for it, which was a second band of chrome for one screen. The filter state moved up to `MeetShell` with the button |
| `S-09` | Typeahead search over swimmers and clubs, from a local index over the start list | must | `done` | `SuggestionIndex` off the `S-01` payload — no request, no debounce; `SearchFold` folds query and name alike. **Not a `SearchBar`**: that control is built to filter the content on screen and this field filters nothing — it adds a term to a list, the way a composer takes a recipient — so tapping it would swap the bar for a search presentation over a body with nothing new in it. A plain `OutlinedTextField` with a leading glass and an explicit clear, never autocapitalising, since the match is folded |
| `S-10` | Suggestions show type (swimmer/club), name, and club; already-added ones are marked and inert | should | `done` | a `ListItem` per row: the type as `overlineContent`, the name as the headline, the club as supporting text. An already-added one loses its click and dims all three lines behind a tick, where it used to stay full-contrast with a tick beside it |
| `S-11` | Active filters appear as chips; tapping a chip's × removes it | must | `done` | Material `InputChip`s in a `FlowRow`. Each carries `minimumInteractiveComponentSize()`, which grows the *touch* target to 48dp without growing the chip: a chip is 32dp tall and they sit shoulder to shoulder, so a slightly-off tap removed the wrong swimmer |
| `S-12` | A count badge on the filter button shows how many filters are active | should | `done` | a Material `Badge` on the app bar's filter button, where it was a hand-drawn rounded rectangle inside an `OutlinedButton` |
| `S-13` | Filters are OR-ed: a lane matches if it hits *any* club or swimmer filter | must | `done` | `ScheduleFilter.laneMatches` (tests) |
| `S-14` | A swimmer filter matches relay members, not just the lane's display name | must | `done` |  |
| `S-15` | With filters on, non-matching lanes are hidden and heats with no match disappear | must | `done` |  |
| `S-16` | All heats toggle: keep every heat visible, still filtering the lanes inside | should | `done` | a `FilterChip` sharing one `FlowRow` with `S-17`, so a long translation wraps rather than clipping, and the selected one carries a tick as well as the fill |
| `S-17` | Upcoming toggle: hide every heat listed *ahead* of the current one, keeping that one | should | `done` | by position; no change when the current heat is unknown (tests) |
| `S-18` | Reset clears filters and both toggles, behind a confirmation | should | `done` | `AlertDialog` with `reset_confirm`; the button is disabled outright when there is nothing to clear |
| `S-19` | Distinct empty states for "no swimmers match these filters" and "no search results" | should | `done` | `no_search_results` as a row in the filter sheet, `no_matches` on the list as an `EmptyState` offering `mobile.reset_filters` as its action when there is something to clear |
| `S-20` | Filters live only for the session — not persisted | should | `done` | `remember(meet.session)` in `MeetShell` — it moved up with `S-08`'s button and is still discarded with the meet; nothing persisted |

## 5.3 Refresh

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `S-21` | A new schedule from the Pi refreshes the list | must | `done` | `schedule_update` → re-fetch, the `S-09` index rebuilt with it, filters retained where names still exist |

## 6. Connection and session

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `C-01` | Three independent sockets: `/ws/scoreboard`, `/ws/results`, `/ws/schedule` | must | `done` | `MeetSession`: three `SplouchSocket`s |
| `C-02` | `join_meet {meet_id, vid}` on every connect, including every reconnect | must | `done` | on every `Connected`, cloud only (`SessionTests`) |
| `C-03` | Automatic reconnect, capped exponential backoff (web: 500ms → 5s) | must | `done` | 500ms → 5s (`SplouchSocketTests`) |
| `C-04` | Heartbeat `ping` every 15s; no inbound frame for 35s means dead — close and reconnect | must | `done` | 15s ping, 35s stale (`SplouchSocketTests`) |
| `C-05` | On foreground or network-restored: probe with a `ping`; no `pong` within ~4s means dead | must | `done` | `wake()` on foreground (`ProcessLifecycleOwner`) and network restored (`ConnectivityManager`); no frame in 4s → abort and reconnect |
| `C-06` | Frames sent while disconnected are queued and flushed on connect | should | `done` |  |
| `C-07` | Unknown events are ignored, not treated as errors | must | `done` | unknown events fall through `when` branches |
| `C-08` | `reload` → re-fetch config and redraw (web: full page reload) | must | `done` | `reloads` → config re-fetch → theme and labels re-derived |
| `C-09` | `meet_live` gates live affordances; a `disconnect` implies `meet_live = false` | must | `done` | `Disconnected` → `meetLive=false`, clock stopped, results wiped |
| `C-10` | Anonymous per-install, per-server id (`vid`) sent with `join_meet` | must | `done` | `PrefsVidStore`: random UUID per normalised origin, minted on first `join_meet`; nothing device-derived |

## 7. Theme and language

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `T-01` | Palette from the meet's config: `bg`, `header_bg`, `header_border`, `header_label`, `header_value`, `th_text`, `th_bg`… | must | `diverges` | `BoardColors(dark)` over `Theme.palette(dark)`. **Departed from, deliberately (`P-15`).** The app does not render `settings.theme_colors`. The reader picks Dark, Light or Automatic and that choice holds **everywhere**, because a preference the next meet could overrule is not a preference — a spectator who chose Light got it until they opened a meet, which is where they were going. The two palettes are the server's own (`DEFAULT_THEME_COLORS` in `server/state.py`, `server/themes/white.toml` minus the two `connection_lost*` keys only the Qt display draws) so a board still looks like Splouch either way round. The field is still decoded and still reaches `Theme.colors`; nothing draws from it. **The cost, named rather than hidden:** an operator who themes a meet in club colours sees it on the web board and the Pi display and not here. The web board and the Pi are unchanged, and `app.md` still needs the matching edit — which is what `diverges` asserts. Tests: `the two palettes are the server's, not the meet's`. One key in the dark table differs from the server's on purpose: `header_label` is `#ffffff` where `state.py` has `#3b9eff`. Both this app and its iOS twin have carried white since before the palette was pinned, and every dark board anyone has seen on a phone has had white EVENT and HEAT words over it — changing it now would restyle the board to fix a number nobody is reading. `BoardTheme.meetScheme` still derives the Material roles a sheet or dialog drawn over the board uses from the palette in effect, so they belong to the same screen |
| `T-02` | Schedule-specific colours `schedule_event`, `schedule_time`, `schedule_name`, `schedule_club`, each with a built-in default | should | `diverges` | `HeatCard` uses `scheduleEvent`/`Time`/`Name`/`Club` from whichever of the two palettes is in effect. **Departed from, deliberately (`P-15`).** The app does not render `settings.theme_colors`. The reader picks Dark, Light or Automatic and that choice holds **everywhere**, because a preference the next meet could overrule is not a preference — a spectator who chose Light got it until they opened a meet, which is where they were going. The two palettes are the server's own (`DEFAULT_THEME_COLORS` in `server/state.py`, `server/themes/white.toml` minus the two `connection_lost*` keys only the Qt display draws) so a board still looks like Splouch either way round. The field is still decoded and still reaches `Theme.colors`; nothing draws from it. **The cost, named rather than hidden:** an operator who themes a meet in club colours sees it on the web board and the Pi display and not here. The web board and the Pi are unchanged, and `app.md` still needs the matching edit — which is what `diverges` asserts. Tests: `the two palettes are the server's, not the meet's` |
| `T-03` | Three font roles — `family` (text), `digits` (clock), `timing` (times and deltas) | must | `done` | six faces bundled in `res/font`, unknown → system monospace. The three roles are the board's; the picker, the sheets and every standard control use the system face, which is what a platform control is for |
| `T-04` | Column headers and header labels are the server's words, never the app's | must | `done` | `settings.labels` as sent, or the `/i18n/{lang}` table once the user picks a language (`Labels.resolve`); the EVENT and HEAT headers come from that table in the style `T-09` is set to. Both sources are the server's and nothing else is layered over them — there is no per-meet override |
| `T-05` | The app's own chrome — tab names, empty states, filter UI — is fetched and cached, not translated in the app | must | `done` | Every word the web page also shows comes from `GET /i18n/{lang}` → `mobile` through `StringTable`, cached with its ETag; words about the app or the device — the server sheet, connection and address errors, the standard buttons — are native resources in `values`, `values-fr` and `values-es`. `SnapshotCoverageTests` fails the build if the app asks for a `mobile` key the captured snapshot lacks |
| `T-06` | Language defaults to the meet's locale and the user may override it | must | `done` | `prefs.lang ?: settings.locale ?: device` |
| `T-07` | Missing theme keys fall back to the documented defaults rather than rendering unstyled | must | `done` | `Theme.DEFAULT_COLORS` / `DEFAULT_FONTS` |
| `T-08` | A language control, per device, applying to every meet opened afterwards | should | `done` | `PrefsSheet` on the picker, `GET /locales`; `ListItem` rows made `selectable`, and no Done button — a `ModalBottomSheet` closes on a swipe, the scrim or back, and the choice applies as it is made |
| `T-09` | A short/long control over the EVENT and HEAT headers only, per device, starting from long | should | `diverges` | **There is no control: the labels are always long.** Withdrawn from `PrefsSheet` on 2026-09-15, matching `Splouch-ios` (2026-09-14). Withdrawn, not deleted: `Preferences.effectiveLabelStyle` returns `Labels.LONG` and reads *over* the stored `labelStyle` rather than rewriting it, so a user who had chosen short still has that choice on disk and gets it back the day the control returns. Reverting is putting the two rows back in `PrefsSheet` and returning `labelStyle` from that property — `Labels.resolve`, `AppModel.setLabelStyle`, the decode path and the store are untouched and still covered (`AppModelTests` asserts both halves: the choice round-trips, and the rendered header stays long). `settings.label_style` is still decoded and still not consulted. The server needs nothing: `prefs_labels`, `prefs_short` and `prefs_long` are still served and still in the snapshot. app.md's T-09 note explicitly permits this |
| `T-10` | A built-in snapshot of the strings is the floor: compiled into the app, refreshed from the server, cached to disk | must | `done` | `core/src/main/resources/i18n/*.json` captured by `scripts/update-strings.sh`; `FileBundleCache` with ETag revalidation |
| `T-11` | The event name follows the chosen language, composed from parts the server sends | should | `done` | `EventName.compose` (tests) |

## 8. Accessibility

`app.md` carries no IDs for this, so there is nothing to join against and nothing below
claims one — IDs are the key across three repos and are not invented here. Recorded so the
next person knows what was done and what was only started.

**Read on a device (2026-09-16), not yet heard.** TalkBack was installed and bound over the
app, and the accessibility tree it consumes was dumped on the board, the start list and the
picker's menu (`adb shell uiautomator dump`). That settles *what a reader lands on and what
each node says* — the findings below are from those dumps. It does not settle prosody,
ordering under a swipe gesture, or whether a lane **sounds** right, which only ears do. Note
that with TalkBack on, `adb shell input tap` moves focus rather than activating, so scripted
navigation needs a double tap.

| Area | State | Notes |
| --- | --- | --- |
| Board rows | done | **Confirmed in the tree:** eight lanes, eight nodes, each the whole lane — `LN 1, Relay Team A, Tremblay… · Roy · Gagnon · Côté, CLUB CAMO, TIME 0:06.00, DIFF -1.10, PL 1`. A lane is one accessibility element saying the whole lane, not six `Text`s read as unrelated fragments (`BoardGrid.spoken`, applied with `clearAndSetSemantics`). The sentence is composed from the server's own column words (`T-04`), so it is spoken in the meet's language rather than the app's; an empty lane says only its number, which is what `L-09`'s blank row means |
| EVENT / HEAT | done | The word and its number read as one, in both the portrait header and the landscape bar, and say nothing at all before a number arrives rather than stopping the reader on a blank |
| Loading versus absence | done | A state that is waiting on something shows a spinner and one reporting an absence shows a glyph — `EmptyState(loading = true)` against `EmptyState(icon = …)`. The server handshake used to say "Checking…" in bare text while the add-a-server field two screens away already made the distinction |
| Hit targets | done | The filter chip's remove target is grown to 48dp with `minimumInteractiveComponentSize()`, which leaves the chip 32dp. That is the one that mattered — chips sit shoulder to shoulder, so a slightly-off tap removed the wrong swimmer. Every other tappable thing is a Material component and already carries the floor |
| Decorative glyphs | done | The meet row's chevron is gone entirely rather than merely hidden from the reader. Tab icons pass `null` as their description because the label beside them is the name |
| Start-list rows | done | A lane on a card was **four unlabelled fragments** — `1`, `Roy · Gagnon`, `CAMO`, `NT` — four stops with nothing saying which was a club and which a seed time, and `NT` alone tells a listener nothing at all. It is one sentence now (`spokenLane`), composed from the server's own column words like `BoardGrid.spoken` (`T-04`): `LN 1, Roy · Gagnon, CLUB CAMO, TIME NT`. The card's heading is one utterance too, and **in the long words**: `EV`/`HT` are a width decision about a phone screen, and a reader has no width problem to solve, so the eye gets `EV 7  HT 1` and the ear gets `EVENT 7, HEAT 1, 4x50 m Freestyle Relay, 09:31`. That was a regression this same pass introduced when the heading went short — caught in the tree, not on screen. One card's stops went from 96 nodes to 30 |
| Measuring rulers | done | The hidden rows that size a lane (`L-15`) and the hidden seed-time ruler that sizes the schedule's timing column (`S-02`) are laid out but never drawn, and both carry `clearAndSetSemantics { }`. Confirmed absent from the tree: a start list of twenty `NT` lanes yields twenty `NT` nodes, not forty, and the board's two rulers add no lane. A ruler read aloud before every row would be worse than the misalignment it fixes |
| Headings | done | Heat headings, the picker's branding title and every `EmptyState` title carry `heading()`, so the rotor can jump heat to heat on the screen whose whole purpose is finding one swimmer among several hundred |
| Selection | done | The server and language rows are `selectable` with `Role.RadioButton`, so the current choice is announced. The radio button was the only thing marking it, and a glyph says nothing out loud. `P-15`'s three Appearance rows repeat the same mistake and the same fix: the check glyph beside the current one said nothing, and the first attempt at `Modifier.semantics { }` never reached the node a reader lands on — it needs `mergeDescendants = true`. Confirmed in the tree: the chosen row is `checkable=true checked=true`, the other two `checked=false`. Compose maps `Role.RadioButton` + `selected` onto `checkable`/`checked`, not onto `selected`, which is worth knowing before reading a dump |
| Labels that lied | done | Three content descriptions said the wrong thing, all of them introduced by the native-UI pass: the filter field's clear button announced "Cancel" when it empties a field, and the server sheet's submit tick "OK" when it adds a server. `clear` and `close` are native strings now (T-05), in all three tables |
| Font scale | done | **Scaling happens once, and which half of the app does it is now one rule.** Every size on the picker, the sheets and the schedule is `sp` off the Material type scale and follows the device's font-size setting. The board does not: it takes its sizes from the height it has — the screen's, the app bar's, or the share each lane got — and puts each through `toSp()`, which divides the setting back out. The header had done this since the native-UI pass; the lane rows had not, and that was the open part. They took their size from the height they share (`L-15`, `L-16`) and then declared it in `sp`, so the setting multiplied a fraction of a `dp` height inside a row that had not grown at all. `L-17`'s auto-shrink hid it in the name cell; the club, time, delta and place cells have no such give, and by 2.0× they were running out of the row. The landscape column titles went the same way (13`dp`, not 13`sp`) — a header that grew with the setting took its height from the lanes underneath it. The board is a display and sizes itself from the height it has; the setting reaches everything a reader reads as text. **There were two scaling channels, not one**, and the second only showed up on a device. Material's `Text` inherits `LocalTextStyle`, whose `lineHeight` is a fixed `24.sp` off the type scale — and `sp` keeps following the setting however the `fontSize` beside it was worked out. So a board that had carefully sized itself from the height it has still got a **48dp line box for 15dp type** at 2×, and every row overflowed the height it had been measured into; the times under the names were cut off at the stripe. `BoardType` hands the line box back to the font (`lineHeight = Unspecified`), which makes it proportional to the one number the board controls. The same pass found `AutoSizeText`'s `8.sp` floor sitting under a `dp`-derived ceiling: above 1× the range inverts and the text is drawn *bigger* than its cell was measured for, so the board's callers now derive the floor from the same pinned size and `AutoSizeText` refuses an inverted range outright. This mirrors the iOS twin's `Font.custom(_:fixedSize:)` fix, where a bundled face scaled itself *and* the caller scaled it again, squaring the setting — same shape of bug, one layer down |
| Haptics | partial | The swipe that removes a saved server fires one when it crosses its threshold, which is how Android answers a committed gesture. Nothing else in the app does — not the pager settling on a tab, not a filter being added |
| Remove animations | partial | The picker's live dot honours it (`ui/common/Motion.kt`). The board's `L-11` lock flash and `L-12` pulse do not, on purpose — those two are information rather than decoration: the flash is how a final time announces itself and the pulse is how a lane says its clock has gone quiet |
| Contrast | done | The board no longer renders whatever it is sent (`P-15`, `T-01`): it draws one of two palettes, both the server's own, so their contrast is a fixed and checkable property of this app rather than the operator's to get right meet by meet. The chrome is the platform's and inherits the platform's. Two colours that had assumed a dark board went with the change — a running time was a fixed `#A0A0A0` and the `L-11` lock flash a fixed white, which on the light board is pale grey on near-white and a flash that cannot be seen at all. Both come off `row_text` now, so each dims or flashes against the row it is actually drawn on. **One measured shortfall, and it is the app's now.** Every pair in the light palette clears WCAG AA-large (3.0); in the dark one, `th_text #666666` on `row_even #202020` is **2.84** — the club cell on every even row, and the same pair on `th_bg` is 3.03, only just over. It is the server's own number (`DEFAULT_THEME_COLORS`), inherited rather than invented here, and raising the club's size to the name's (`L-15`) at least puts it under the large-text bar rather than the 4.5 body one. Left as it is rather than quietly restyling the board a second time, but it is a real shortfall and the fix is a lighter `th_text` in the dark table |


## Open questions for the contract

Raised while building this app; each needs an answer in `app.md` or `api.md` (or in
`shared/locales/`) rather than here. Until then the app does what the note says.

1. **`P-03` versus `R-02`.** `GET /meets` keeps offline meets "so an attendee can read
   the last known state" (api.md §5.6), but `R-02` wipes the Results board the moment
   `meet_live` is false, which is exactly what an offline meet replays on join. The web
   does the same, so this app follows `R-02`: on an offline meet the Scoreboard holds
   its last frame and the Results tab shows "Waiting for results…". If the intent is to
   keep the last results readable, `R-02` needs an exception for a retained meet.

2. **`S-21` — the Pi does not announce a start list loaded by a test session.**
   `schedule_update` is emitted only from the meet-file upload route
   (`server/routes/settings.py`); `POST /test_play`'s companion LENEX changes
   `GET /schedule.json` without it. A phone that was open before the session started
   keeps "No schedule available yet." until pull-to-refresh (`A-05`). Server-side.

3. **The cloud's join replay is a partial snapshot.** Against a local cloud relaying a
   Pi test session, `join_meet` replayed `meet_live` and an `update_scoreboard` carrying
   only `lane_time`, `lane_place`, `lane_delta*` and `lane_running` keys — no
   `current_event`, `current_heat`, `event_name` or `lane_name*`. A late joiner sees
   finals and places with an empty header and no names until the next heat change.
   `api.md` §3 says the server replays "the latest cached snapshot"; either the cache
   should keep every key it has seen, or `L-13`'s baseline note should say a replay may
   lack the header. Server-side; the app renders what it receives.

4. **`T-09` — answered, and this app now goes further.** app.md was revised while
   `Splouch-ios` was built: the third "meet default" row is gone and the control starts
   from long, which is what this app already did. The note also says in as many words that
   a release may withdraw the control provided it does not discard the preference behind
   it — so the withdrawal above needs no contract change, only the `diverges` row.

5. **`A-07` is written on orientation; Android decides on width.** The row says landscape
   drops the tab labels to save height. Material's answer for a window that is short and
   wide is not smaller tabs but tabs somewhere else — a navigation rail — and that is what
   this app now does, keyed to `WindowWidthSizeClass` rather than to orientation. The
   requirement underneath still holds ("the tabs must stop costing height on a short
   window"); the mechanism in the row does not. `app.md` A-07 needs re-wording to the
   effect rather than the markup, the way §0.4 asks.

6. **`L-15` and `L-16` are written on orientation too, and a tablet breaks that.** Portrait
   gets the two-line compact row and landscape the full table, on the stated assumption
   that the only variable is which way the phone is held. On a 10" tablet or an unfolded
   foldable, *portrait* is wide enough for the full table, and the compact row there wastes
   most of the screen. This app has not changed the behaviour — both still branch on
   orientation — because the fix is a contract decision, not an implementation one:
   `L-15`/`L-16` should either say "narrow" and "wide" instead of "portrait" and
   "landscape", or say that tablets are out of scope, which is also an answer worth
   writing down. `Splouch-ios` ships to iPad and raised the same question there, so it
   wants one answer for both repos. **Until it has one, a tablet gets phone layout.**


Also noted, not blocking: the Pi's `GET /config` carries no `label_style`, which now
costs nothing either way — `T-09` starts from long on every server; `T-07`'s "documented
defaults" exist only in code (`cloud_server.py` `_DEFAULT_COLORS`), which this app
mirrors in `core/.../theme/Theme.kt`; podium tints exist in `results.html` and the
palette but not in the contract, so they are left out.
