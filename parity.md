# Splouch for Android — parity ledger

One row per feature ID in [`app.md`](../Splouch/docs/app.md) **v1**, the behaviour contract.
That document owns *what* each feature is and whether it is required; this file owns
*whether it is implemented here, and why not* (`app.md` §0.1).

**Stack:** Jetpack Compose, Kotlin

Status is one of `done` / `deferred` / `n/a — <reason>`. IDs are the join key across
the three repos and are never renumbered — a row that goes away keeps its ID and gains
a note. Rows already marked `n/a` below are the ones the contract itself puts out of
scope for a native client; everything else starts `deferred`.

`Level` is copied from `app.md` v1 for triage only. **`app.md` is authoritative** —
if the two ever disagree, that document wins and this one is stale.

**Verification status (2026-09-13):** `./gradlew test` passes (74 unit tests over the
socket loop, race clock, frame merge, results grid, strings, filters, the app model and
the string-snapshot coverage check) and the two live checks in `LiveServerTests` pass against a local Pi server (handshake,
config, schedule, connect burst, joined session). `./gradlew :app:assembleDebug` builds.
Run on a Medium Phone AVD (API 37) against a local Pi playing a recorded session and
against a local cloud relaying it: the Pi session opens straight to the board, live frames
merge, the race clock ticks between re-bases in the running grey, the picker lists live
and offline meets with the server's strings and disclaimer, the Schedule tab highlights
the current heat and the filter sheet's typeahead answers. Landscape shows the full table
with the header row and drops the tab labels. A heat change blanked the board and the
Results tab then held the finished heat with locked times, deltas and places; the tab
choice survived a relaunch. After the i18n rework (2026-09-13) the split was checked on
the same AVD against splouch.ca: the picker's disclaimer and privacy note, the tab
names, the filter sheet and the language and label-style controls all render the
server's French, while the server sheet and the standard buttons follow the device
locale (English by default, French under a per-app locale). The `T-09` rework
(2026-09-13) was checked on the same AVD against a local Pi: the label section offers
two rows, a device carrying the old absent preference comes up on **Longues** and the
board header reads `ÉPREUVE SÉRIE`, picking **Courtes** turns it into `ÉP SÉR`, and the
choice survives leaving and reopening the sheet. Not yet exercised on a
device: the lock flash and the pulse, a language change made from the preference
sheet mid-meet, the mDNS browse, and A-09.

Suggested order: `P-13` (handshake) → `C-01`–`C-05` (sockets) → `P-01`/`P-08` (meet
list, open a meet) → `L-01`–`L-14` (scoreboard). Results, Schedule and the `T-*`
language controls reuse all of it.


## 1. Meet picker

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `P-01` | List of meets as cards: name, date, location, sport | must | `done` | `PickerScreen`: cards from `GET /meets` |
| `P-02` | Per-meet picker image on the card, when the meet supplies one | should | `done` | `ImageCache` fetches `/picker_image/{id}` when `has_picker_image` |
| `P-03` | Offline meets stay listed, marked with a dimmed status dot | must | `done` | dimmed ring for `offline`, pulsing green dot for live |
| `P-04` | Empty state when no meets are active | must | `done` | `strings.no_meets` from `/picker/config` |
| `P-05` | Picker branding: title, logo, logo above or below the title | should | `done` | title, `/picker_logo`, `logo_above` |
| `P-06` | Unofficial-results disclaimer under the list | must | `done` | server text from `/picker/config`, falling back to the same key in the `GET /i18n/{lang}` snapshot; never a compiled copy |
| `P-07` | Privacy note, shown whenever attendance counting is on for this server | must | `done` | gated on `analytics_enabled` |
| `P-08` | Selecting a meet opens the app shell for it | must | `done` | `AppModel.openMeet` → `GET /meet/{id}/config` → `MeetSession` |
| `P-09` | Pull-to-refresh re-fetches the meet list | should | `done` | Material `PullToRefreshBox` |
| `P-10` | Install hand-off: store links to the native iOS/Android apps once they ship, Add-to-Home-Screen until then | web-only | `n/a` | web-only — an app satisfies it by existing (app.md §0.3) |
| `P-11` | Choose which server to connect to, from a list, in the picker's menu | native-only | `done` | `ServerSheet`: default + saved + `GET /servers` + nearby; header shows the server when not the default |
| `P-12` | Servers on the local network are offered without anyone typing an address | native-only | `done` | `NsdBrowser` on `_splouch._tcp`, dialled by `.local` host name (option A); the platform exposes the host name from Android 14 only, so older devices see nothing and add the Pi by hand |
| `P-13` | A server can be added by hand, checked before it is saved | native-only | `done` | `AppModel.addServer`: parse, `GET /server`, then save; `http` accepted for `.local` names only |

## 2. App shell

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `A-01` | Three tabs — Scoreboard, Results, Schedule — each with icon and label | must | `done` | `NavigationBar`, Tabler icons ported as vectors, labels from `mobile.*` |
| `A-02` | Back affordance to the meet picker | must | `done` | back item in the bar and the system back gesture → `closeMeet` |
| `A-03` | Horizontal swipe moves between adjacent tabs, and the movement is visible — the tabs follow the finger and settle on release | must | `done` | `HorizontalPager`, full width, follows the finger |
| `A-04` | The selected tab survives a relaunch | should | `done` | `prefs.tab` via `AppModel.setTab` |
| `A-05` | Pull-to-refresh re-fetches config and rejoins the sockets | should | `done` | `PullToRefreshBox` → `refreshMeet`: config re-fetch, socket probe, schedule reload |
| `A-06` | Content clears notch, Dynamic Island, and home indicator | must (free natively) | `done` | edge-to-edge with `Scaffold` insets |
| `A-07` | Portrait stacks label under icon; landscape drops labels to save height | should | `done` | labels dropped in landscape |
| `A-08` | Window and home-screen title is the meet's `app_window_title`, falling back to its `name` | web-only | `n/a` | web-only — an app satisfies it by existing (app.md §0.3) |
| `A-09` | Meet goes offline mid-session → return to the picker | must | `done` | config re-fetched on reconnect, foreground, pull-to-refresh and `reload`; a 404 on a cloud closes the meet (`AppModelTests`) |

## 3.1 Header

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-01` | EVENT number, HEAT number, each a small label above a large value | must | `done` | `BoardHeader` |
| `L-02` | Event name | must | `done` | `event_name`, composed from parts when a language is chosen (T-11) |
| `L-03` | Wall clock, `HH:MM`, ticking every second | must | `done` | device time, ticks each second |

## 3.2 Lane table

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-04` | One row per lane, `num_lanes` rows always present | must | `done` | `ScoreboardState(numLanes)` |
| `L-05` | Columns: lane · name (+ alt sub-line) · club · time · delta · place | must | `done` | `BoardGrid` |
| `L-06` | Relay member names on a dimmed second line under the name | must | `done` | `lane_name_alt<i>` dimmed under the name |
| `L-07` | Column visibility follows config: `show_name`, `show_club`, `show_delta`, `show_position` | must | `done` | `show_*` from settings |
| `L-08` | Column *headers* hide independently of the columns: `show_*_header` | should | `done` | `show_*_header`, landscape header row |
| `L-09` | Empty lanes render blank in place — rows never collapse or shift | must | `done` | rows are positional; blanks stay in place |
| `L-10` | Frames are partial: merge changed keys into local state, never replace | must | `done` | `ScoreboardState.apply` merges keys (`ScoreboardStateTests`) |
| `L-11` | A running lane's time is styled distinctly; on stop it plays a one-shot "locked" transition, cancelled if the lane starts running… | must | `done` | grey while running, white→time flash on the stop edge keyed by `lockEdge`, cancelled when running again |
| `L-12` | Every running lane's time cell shows the race clock: one value for the heat, re-based by the server every couple of seconds and… | must | `done` | `RaceClock` over a monotonic mark, 10Hz ticker in `MeetSession`, frozen forward at 3 sync intervals, pulse fallback, stopped on background (`RaceClockTests`, `ScoreboardStateTests`) |
| `L-13` | Event or heat change blanks all times, deltas, and places | must | `done` | baseline on connect, running-hold, otherwise blank (`ScoreboardStateTests`) |
| `L-14` | Returning to the tab re-runs layout and refreshes the clock | must (native: on-appear) | `done` | `revealScoreboard` on page settle |

## 3.3 Layout

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-15` | Portrait: two-line compact row — lane number spanning left, name on line 1 with club right-aligned, time and delta and place on… | must | `done` | `PortraitGrid` |
| `L-16` | Landscape: full table with a header row, row font scaled to lane count | should | `done` | `LandscapeGrid`, row font from lane count |
| `L-17` | Long names shrink to fit their cell, ellipsis only as a floor | must | `done` | `BasicText` `autoSize`, ellipsis only below 8sp |

## 3.4 Not on this tab

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `L-18` | Carousel / fullscreen image overlay | n/a | `n/a` | images are local to the Pi and are never relayed |
| `L-19` | Podium highlight animation | n/a | `n/a` | Pi-local, `race_finished` is not forwarded |
| `L-20` | Animated column show/hide, operator-driven | n/a | `n/a` | cloud columns are always visible |
| `L-21` | Any timed hold on a state — the kiosk's 3s `brief_results` flash, its results pause, its leave-results debounce | n/a | `n/a` | still real on the kiosk, still deliberately absent here: the phone shows the last frame received and runs no… |
| `L-22` | Independent per-lane clock, each lane timing its own length | n/a | `n/a` | the console has one race clock and the lanes mirror it; a lane's own figure exists only as its split… |

## 4. Results tab

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `R-01` | Until the first snapshot: an empty grid, with "Waiting for results…" below it wherever there is room to say so | must | `done` | empty grid, `waiting_results` below it in portrait |
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
| `S-01` | Every heat as a card: scheduled time, "Event N — Heat M", event name | must | `done` | `ScheduleTab` from `/meet/{id}/schedule` or `/schedule.json` |
| `S-02` | Each card lists its lanes: lane number, name, club, seed time | must | `done` |  |
| `S-03` | Relay entries show member first names joined by `·` | should | `done` | `ScheduleFilter.displayName` |
| `S-04` | Alternating card backgrounds, computed over *visible* cards so filtering keeps the stripe | should | `done` | stripe over visible cards |
| `S-05` | The heat the meet is on is highlighted in the list | must | `done` | `MeetSession.currentHeat` off both sockets, strings compared |
| `S-06` | The list auto-scrolls to the current heat once per appearance | must | `done` | once per appearance, re-armed on resume |
| `S-07` | Empty state when no meet file is loaded | must | `done` | `mobile.no_schedule` |

## 5.2 Filtering

| ID | Feature | Level | Status | Notes |
| --- | --- | --- | --- | --- |
| `S-08` | Full-screen filter sheet, opened from a button in the top bar | must | `done` | `FilterSheet`, full-screen dialog |
| `S-09` | Typeahead search over swimmers and clubs, from a local index over the start list | must | `done` | `SuggestionIndex` off the `S-01` payload — no request, no debounce; `SearchFold` folds query and name alike |
| `S-10` | Suggestions show type (swimmer/club), name, and club; already-added ones are marked and inert | should | `done` |  |
| `S-11` | Active filters appear as chips; tapping a chip's × removes it | must | `done` |  |
| `S-12` | A count badge on the filter button shows how many filters are active | should | `done` |  |
| `S-13` | Filters are OR-ed: a lane matches if it hits *any* club or swimmer filter | must | `done` | `ScheduleFilter.laneMatches` (tests) |
| `S-14` | A swimmer filter matches relay members, not just the lane's display name | must | `done` |  |
| `S-15` | With filters on, non-matching lanes are hidden and heats with no match disappear | must | `done` |  |
| `S-16` | All heats toggle: keep every heat visible, still filtering the lanes inside | should | `done` |  |
| `S-17` | Upcoming toggle: hide every heat listed *ahead* of the current one, keeping that one | should | `done` | by position; no change when the current heat is unknown (tests) |
| `S-18` | Reset clears filters and both toggles, behind a confirmation | should | `done` | `AlertDialog` with `reset_confirm` |
| `S-19` | Distinct empty states for "no swimmers match these filters" and "no search results" | should | `done` | `no_search_results` in the sheet, `no_matches` on the list — English floor until the server has the keys (see below) |
| `S-20` | Filters live only for the session — not persisted | should | `done` | `remember` in the tab, nothing persisted |

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
| `T-01` | Palette from the meet's config: `bg`, `header_bg`, `header_border`, `header_label`, `header_value`, `th_text`, `th_bg`… | must | `done` | `BoardColors` |
| `T-02` | Schedule-specific colours `schedule_event`, `schedule_time`, `schedule_name`, `schedule_club`, each with a built-in default | should | `done` |  |
| `T-03` | Three font roles — `family` (text), `digits` (clock), `timing` (times and deltas) | must | `done` | six faces bundled in `res/font`, unknown → system monospace |
| `T-04` | Column headers and header labels are the server's words, never the app's | must | `done` | `settings.labels` as sent, or the `/i18n/{lang}` table once the user picks a language (`Labels.resolve`); the EVENT and HEAT headers come from that table in the style `T-09` is set to. Both sources are the server's and nothing else is layered over them — there is no per-meet override |
| `T-05` | The app's own chrome — tab names, empty states, filter UI — is fetched and cached, not translated in the app | must | `done` | Every word the web page also shows comes from `GET /i18n/{lang}` → `mobile` through `StringTable`, cached with its ETag; words about the app or the device — the server sheet, connection and address errors, the standard buttons — are native resources in `values`, `values-fr` and `values-es`. `SnapshotCoverageTests` fails the build if the app asks for a `mobile` key the captured snapshot lacks |
| `T-06` | Language defaults to the meet's locale and the user may override it | must | `done` | `prefs.lang ?: settings.locale ?: device` |
| `T-07` | Missing theme keys fall back to the documented defaults rather than rendering unstyled | must | `done` | `Theme.DEFAULT_COLORS` / `DEFAULT_FONTS` |
| `T-08` | A language control, per device, applying to every meet opened afterwards | should | `done` | `PrefsSheet` on the picker, `GET /locales` |
| `T-09` | A short/long control over the EVENT and HEAT headers only, starting from short | should | `done`, diverges | `PrefsSheet`, two options and no third: the device picks short or long and **starts from long**, so the operator's `label_style` no longer decides where it starts and the old "meet default" row is gone. Still the EVENT and HEAT headers only — every other column keeps the operator's word. Long is the default because these headers read as `EV`/`HT` otherwise, which an attendee has to decode; see open question 4 |
| `T-10` | A built-in snapshot of the strings is the floor: compiled into the app, refreshed from the server, cached to disk | must | `done` | `core/src/main/resources/i18n/*.json` captured by `scripts/update-strings.sh`; `FileBundleCache` with ETag revalidation |
| `T-11` | The event name follows the chosen language, composed from parts the server sends | should | `done` | `EventName.compose` (tests) |


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

4. **`T-09` starts from long here, and the operator's `label_style` is inert.**
   `app.md` says the control starts from short and this app started it from
   `settings.label_style`. On a phone the abbreviations are the wrong default: `EV`
   and `HT` above a number are a puzzle where `EVENT` and `HEAT` are not, and the two
   headers have the width for the long word. So the sheet offers short and long only —
   no "meet default" — and starts from long, on the cloud and the Pi alike. If the
   contract wants the operator's choice honoured, `T-09` needs to say whether the
   device's preference overrides it or merely starts from it.

Also noted, not blocking: the Pi's `GET /config` carries no `label_style`, which now
costs nothing either way — `T-09` starts from long on every server; `T-07`'s "documented
defaults" exist only in code (`cloud_server.py` `_DEFAULT_COLORS`), which this app
mirrors in `core/.../theme/Theme.kt`; podium tints exist in `results.html` and the
palette but not in the contract, so they are left out.
