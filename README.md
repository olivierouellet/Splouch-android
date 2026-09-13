# Splouch for Android

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
  Compose screens (picker and server sheet, meet shell, scoreboard, results, schedule
  and filter sheet). Bundled fonts in `src/main/res/font/`, licences in `font-licenses/`.

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
