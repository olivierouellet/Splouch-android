# Contributing to Splouch for Android

Thanks for taking an interest. This is the spectator client: it reads a meet and shows
it, and it does that on a phone that may be sitting in a noisy pool gallery on a LAN with
no internet, or four hundred kilometres away through the cloud relay. The bar for a change
is not "it compiles", it is "it still reads at arm's length while a heat is swimming".

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## The contracts come first

Two documents in the sibling [`Splouch`](https://github.com/olivierouellet/Splouch) repo
own what this app does, and **neither is ever copied into this tree**:

| | |
| --- | --- |
| [`docs/app.md`](https://github.com/olivierouellet/Splouch/blob/master/docs/app.md) | What a spectator sees and does, one feature per ID (v1) |
| [`docs/api.md`](https://github.com/olivierouellet/Splouch/blob/master/docs/api.md) | Sockets, events, payloads (v2) |

[`parity.md`](parity.md) is this repo's ledger against `app.md`: one row per feature ID,
whether it is built here, and why not. A change that implements, defers or diverges from a
feature updates its row in the same commit. IDs are the join key across the three repos
and are never renumbered.

**Never invent a wire field.** If a payload doesn't carry what you need, the fix is in
`api.md` and the server, not a guess here. If the app has to behave differently from
`app.md` for a reason Android imposes, that is a `diverges` row — build it, write down
what it does instead and why, and say in the PR that `app.md` needs the matching edit.

---

## What helps most

| | |
| --- | --- |
| **Time on a real device** | Nearly all of `parity.md` was confirmed on one AVD — a 411×914dp Pixel-8 viewport. A real phone, a tablet, a foldable, or anything that isn't a mainstream 6-inch screen is the most useful thing you can bring. |
| **Reports from a real meet** | Anything that surprised you in the gallery: a board that stopped updating, a reconnect that didn't, a name that clipped. |
| **Accessibility** | TalkBack order, labels and merge points; font scale at 2.0×; both themes. §8 of `parity.md` says what has been read in the semantics tree and what has actually been *heard* — the gap between those two is real work. |
| **Older Android** | `minSdk` is 26. Bonjour's host name only arrives on Android 14, so `P-12`'s nearby servers are empty below it and the Pi is added by hand — confirming how that feels on an older device is worth a report. |
| **Languages** | The native words live in `app/src/main/res/values{,-fr,-es}/strings.xml`. The served words belong to the server — see [Strings](#strings). |

---

## Setup

```sh
./gradlew :core:test            # the contract layer, JDK 17 alone
./gradlew :app:assembleDebug    # needs the Android SDK
```

That first line is the whole inner loop for most work. `:core` is plain Kotlin with no
Android types, so its 92 tests run on any machine with a JDK 17 — and
`settings.gradle.kts` includes `:app` **only when an SDK is found** (`local.properties`
`sdk.dir`, or `ANDROID_HOME`). Without one you get `:core` and a warning, not a failure.
That is deliberate: a clone with no Android SDK is still a working checkout of the
contract layer.

For the screens you need the SDK, and Android Studio is where they are actually worked on.
[`emulator.md`](emulator.md) has the rest of the loop without it — booting an AVD, driving
it with `adb`, installing, launching against a local server, and the false alarms.

```sh
./gradlew :app:installDebug
adb shell am start -n app.splouch.android/.MainActivity --es server http://10.0.2.2:5055
```

`--es server` points a debug build at one dev server for this launch without touching
stored preferences. The [README](README.md) has the two dev servers and how to push live
frames without a timing console.

---

## Before you open a pull request

```sh
./gradlew :core:test
```

Green on `main`, and expected to stay that way. Two things about that run are worth
knowing, because both of them let a green result mean less than it looks:

**`LiveServerTests` returns early unless `SPLOUCH_LIVE_SERVER` is set.** Each test exits
at its first line without a server, which the report counts as passing. Fine for most
changes, not fine for one that touches `SplouchApi`, `SplouchSocket` or `MeetSession` —
point it at a local Pi or a local cloud for those:

```sh
SPLOUCH_LIVE_SERVER=http://127.0.0.1:5056 ./gradlew :core:test --tests '*LiveServerTests*' --rerun
```

**`:core:test` never compiles `:app`.** The Compose screens, the OkHttp and NSD adapters
and the resources are built by `:app:assembleDebug` and nothing else, so a change in
`app/` can be green locally and not compile. Assemble it before you push.

CI runs both of those on every push and pull request, in two jobs that exist because
each proves something the other cannot see. One has **no Android SDK at all** and asserts
`:app` really is out of the build — the README's claim, checked rather than trusted — and
runs the suite with a floor under the test count, because a test task that discovers
nothing still succeeds. A failing test prints its name and full stack trace into the log,
and the HTML and XML reports are uploaded as an artifact on that run only. The other job
has the SDK, builds the APK, and then checks what no test reads: that the six fonts are
in it, that the network config is, and that no loopback or emulator address has drifted
out of the debug overlay into the release rule. Android Lint runs there too and **gates
nothing** — its six current errors are all deliberate, so read the report for what is new
beside them.

Anything touching a screen also gets installed and looked at, in both orientations if the
layout moved. Say in the PR which device or AVD and what you saw — `parity.md` is written
that way for a reason, and a claim about the UI that nobody looked at is how this tree
gets its worst bugs. §8's two font-scale bugs were both invisible to the suite.

---

## Conventions

These are the ones that trip people up. Each has a reason in the tree.

**`:core` has no Android types.** No `android.*`, no Compose, no OkHttp. The platform
seams are interfaces declared in `:core` — `WebSocketTransport`, `HttpClient`,
`VidStore`, `PreferencesStore`, `BundleCache` — with their adapters in
`app/src/main/kotlin/app/splouch/android/platform/`. That line is what keeps the suite on
a JDK and the contract layer testable without a device; an `import android.` in `core/`
is the one import that fails review on sight.

**Dependencies go in `gradle/libs.versions.toml`**, never as a literal coordinate in a
module's build file. And keep the list short. It is not empty — Compose, AndroidX,
coroutines, kotlinx-serialization and OkHttp are all here — but there is no analytics SDK,
no crash reporter and no third-party UI kit, and adding one is a decision to argue for in
the PR rather than a line in the catalog. The app ships to a phone that may be in a
building with no internet, and every package added is one more thing to audit.

**Chrome is the platform's, the board is the meet's.** `SplouchTheme` is the device's —
dynamic colour, system light and dark — and dresses everything outside a meet;
`BoardTheme` is the meet's palette and faces (`T-01`–`T-03`) and derives the Material
scheme that sheets and dialogs drawn over the board use. The picker has no meet and so no
palette. Read `parity.md` → *The native-UI pass* before reaching for a hex literal.

**Don't format the tree.** There is no ktlint, spotless or detekt here on purpose, and
running a formatter over it rewrites hand-aligned declarations and wrapped argument lists
that are laid out to be read — the same decision the server repo made about `ruff format`.
The settings in `.vscode/` turn format-on-save off for Kotlin for exactly this reason; if
you work in Android Studio, keep Reformat Code off the file you are editing.

**Type that is sized carries its own `lineHeight`.** Material's inherited `24.sp`
`lineHeight` will overflow a row that was measured at a `dp`-derived size, and it does it
only at a font scale nobody ran — both of §8's device-only bugs were this shape. A size
computed from a box sets the line height from the same box.

**The fonts are copies.** `app/src/main/res/font/*.ttf` are byte-identical to
`../Splouch/shared/static/fonts/`, renamed to what Android resource names allow. Refresh
them from there, never edit them here, and a new face arrives with its OFL in
`app/font-licenses/`.

### Strings

Which table a word belongs in is decided by what the word is *about* (`app.md` T-05), and
getting it wrong is the most common mistake in this repo:

* **A word a spectator reads that the web pages also show** — tab names, empty states, the
  filter sheet, the picker's chrome — is **served**, through `GET /i18n/{lang}` and
  `StringTable.mobile`. It is added **on the server first**. The JSON under
  `core/src/main/resources/i18n/` is a captured floor for when the server can't be
  reached; it is regenerated with `scripts/update-strings.sh <base url>` and **never
  edited by hand**. `SnapshotCoverageTests` fails the build if the app asks for a `mobile`
  key the snapshot lacks — that failure means "capture it", not "hand-add it".
* **A word about the app or the device** — the server sheet, nearby servers, connection
  and address errors, the standard buttons — is **native**, an ordinary Android resource
  in `app/src/main/res/values/strings.xml`, with `values-fr` and `values-es`. All three,
  in the same commit.

### Commits

One topic per commit, the area in brackets, and a title that says what changed and reads
as a sentence:

```text
[Board] Twelve lanes fit portrait by giving up the title, the relay name, then size
[Schedule] A heat's clock time leaves the seed-time ruler to the event name
[Theme] The reader picks the palette, not the meet
```

Areas in use: `[Board]`, `[Schedule]`, `[Picker]`, `[Filter]`, `[Sheets]`, `[Shell]`,
`[Theme]`, `[Prefs]`, `[Empty]`, `[Servers]`, `[Icon]`, `[A11y]`, `[Core]`, `[App]`,
`[Build]`, `[Docs]`. Add a body when the *why* isn't obvious from the diff — that is where
this tree keeps its reasoning.

---

## Reporting a bug

Open an issue — the form asks for what's needed: what happened, which screen, which
server, the app version and the Android version, and the one question that routes it
fastest, **did the web scoreboard show the same thing at the same moment?** If it did, the
bug is in the [server repo](https://github.com/olivierouellet/Splouch/issues), not this
one.

For anything security-sensitive, don't open a public issue — follow
[SECURITY.md](SECURITY.md), which also sets out what this app assumes about the network it
is on.

---

## Licence

Splouch for Android is [MIT](LICENSE). Contributions are accepted under the same terms.
The bundled fonts keep their own SIL Open Font Licenses.
