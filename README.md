# Splouch for Android

[![CI](https://github.com/olivierouellet/Splouch-android/actions/workflows/ci.yml/badge.svg)](https://github.com/olivierouellet/Splouch-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

The spectator app for Splouch meets. It follows two contracts that live in the
sibling `Splouch` repo and are never copied here:

- `../Splouch/docs/app.md` — what a spectator sees and does (v1)
- `../Splouch/docs/api.md` — sockets, events, payloads (v2)

[`parity.md`](parity.md) is this repo's ledger: one row per feature ID, whether it
is built here and why not.

## Layout

- `core/` — plain Kotlin (JVM), no Android types: the contract layer, testable with a JDK alone.
  - `wire/` envelope and typed payloads (tolerant decoding, event/heat normalised to strings)
  - `transport/` `WebSocketTransport` behind an interface, and `SplouchSocket`, the
    reconnecting socket loop of app.md §6
  - `clock/` the race clock (L-12)
  - `board/` the scoreboard frame merge and state (L-10..L-13) and the results grid
  - `session/` server address and per-server `vid`, REST client, `MeetSession`
    (three sockets → tab state), `AppModel` (server, picker, open meet, preferences)
  - `strings/` string resolution (T-10), labels (T-04, T-09), event names (T-11),
    the compiled snapshot in `src/main/resources/i18n/`
  - `theme/`, `schedule/`
- `app/` — the Android application: OkHttp adapters, stores, mDNS browse, and the
  Compose screens (picker and its server and language sheets, meet shell, scoreboard,
  results, schedule and filter sheet). Bundled fonts in `src/main/res/font/`, licences
  in `font-licenses/`.
  - `ui/theme/` the two themes and the line between them: `SplouchTheme` is the device's
    (dynamic colour, system light/dark) and dresses everything outside a meet;
    `BoardTheme` is the meet's palette and faces (app.md `T-01`–`T-03`), and derives the
    Material scheme that sheets and dialogs drawn over the board use
  - `ui/common/` the shared empty state and the Remove-animations check
  - `ui/Adaptive.kt` Material's width breakpoints — `Compact` gets the bottom navigation
    bar, anything wider gets the rail (`parity.md` `A-07`)

**Chrome is the platform's, the board is the meet's.** A meet themes its own board and
nothing else; the picker has no meet and so no palette, and is Material throughout. See
`parity.md` → *The native-UI pass* before reaching for a hex literal.

`settings.gradle.kts` includes `:app` only when an Android SDK is found, so
`./gradlew :core:test` runs anywhere a JDK 17 is.

## Building and testing

```sh
./gradlew :core:test            # the contract layer, JDK only
./gradlew :app:assembleDebug    # needs the Android SDK (local.properties or ANDROID_HOME)
```

Dev servers (both default to port 5000):

```sh
cd ../Splouch/server && uv run python app.py                                   # Pi
cd ../Splouch/cloud  && DATA_DIR=/tmp/splouch-cloud uv run uvicorn cloud_server:app --port 5055
```

On the emulator a debug build can be started on a local server without touching
stored preferences:

```sh
adb shell am start -n app.splouch.android/.MainActivity --es server http://10.0.2.2:5055
```

Only `.local` names, `localhost`, `127.0.0.1` and `10.0.2.2` may be dialled over plain
HTTP (release builds: `.local` only). See `app/src/main/res/xml/network_security_config.xml`
and app.md `P-12`.

A server can also arrive by QR code (`parity.md` `P-16`): the code carries
`https://splouch.ca/add?server=<origin>`, a verified App Link, and the app prompts before
it adds anything. **The web half lives in the sibling `Splouch` repo and the feature is
inert without it** — `splouch.ca` has to serve `/.well-known/assetlinks.json` and the
`/add` page that offers the Play Store when the app is not installed. `P-16`'s row says
exactly what those two must contain.

[`emulator.md`](emulator.md) covers the rest of the AVD loop: booting one, driving it
with `adb`, and what to check when the screen looks wrong.

## Strings

app.md T-05 draws the line by what a word is *about*, not by which repo renders it.

- **The server's words** are the ones the web pages also show: the tabs, the empty
  states, the filter sheet, and the picker's chrome and compliance text. The app reads
  them through `StringTable.mobile` from `GET /i18n/{lang}`, cached on disk with its
  ETag, over the compiled snapshot below. Never translate one of these in the app.
- **The app's words** are the ones about the app or the device: the server sheet,
  connection and address errors, and the standard buttons. They are ordinary Android
  resources in `app/src/main/res/values/strings.xml`, with `values-fr` and `values-es`.

`core/src/main/resources/i18n/<lang>.json` are the compiled floor of app.md T-10: the
body of `GET /i18n/{lang}` for each language the default cloud lists, verbatim.
Regenerate them from the default cloud before a release and whenever the server's
`shared/locales` table changes, never by hand:

```sh
scripts/update-strings.sh https://splouch.ca
```

`SnapshotCoverageTests` fails the build when the app asks for a `mobile` key the
snapshot does not carry. The fix is on the server, then a recapture — not a word
added here.

## Community

| | |
| --- | --- |
| [Contributing](CONTRIBUTING.md) | The contracts, setup, the checks a PR must pass, conventions, reporting a bug |
| [Security](SECURITY.md) | Reporting a vulnerability, what the app assumes about the network it is on |
| [Code of Conduct](CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |

## License

The app is MIT licensed — see [`LICENSE`](LICENSE). The bundled fonts are not:
each one keeps its own SIL Open Font License, shipped verbatim in
`app/font-licenses/`.
