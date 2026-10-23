# Agent instructions

For AI coding assistants (Claude Code, GitHub Copilot, Codex, Cursor and others) working in this repository.
People should start with [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/development.md](docs/development.md). Both say more than this file; when they disagree,
they win.

## What this is

The spectator app for Android (Kotlin, Jetpack Compose) for [Splouch](https://github.com/olivierouellet/Splouch)
swim meets: `:core` is the contract layer in plain Kotlin, `:app` the Android app. Its contracts are in the
Splouch repo: `docs/app.md` (features, by ID) and `docs/api.md` (sockets and payloads). They are **never
copied here**. [`parity.md`](parity.md) is this repo's ledger against `app.md`.

## Before you finish

```sh
./gradlew spotlessApply                       # format; CI runs spotlessCheck
./gradlew :core:test
./gradlew :app:assembleDebug :app:lintDebug   # needs the Android SDK
uvx rumdl@0.2.77 check .
```

`:core:test` never compiles `:app`, so an `app/` change can be green there and not compile. Both modules
treat warnings as errors. `LiveServerTests` returns early without `SPLOUCH_LIVE_SERVER` and still counts as
passing: not enough for a change to `SplouchApi`, `SplouchSocket` or `MeetSession`. See
[`emulator.md`](emulator.md) to look at a screen change.

## Conventions

* **Never invent a wire field.** If a payload lacks something, the fix is in `api.md` and the server. A
  behaviour Android forces away from `app.md` is a `diverges` row in `parity.md`.
* A change that implements, defers or diverges from a feature updates its `parity.md` row in the same
  commit. Feature IDs are the join key across the three repos: never renumber them.
* **`:core` has no Android types:** no `android.*`, no Compose, no OkHttp. Platform seams are interfaces in
  `:core`, with adapters in `app/src/main/kotlin/app/splouch/android/platform/`.
* Dependencies go in `gradle/libs.versions.toml`, and a new one is argued for in the PR.
* ktlint (through Spotless) owns layout. A change to `.editorconfig` reformats the tree: its own commit.
* Type that is sized from a box sets its `lineHeight` from the same box.
* **Strings:** a word the web pages also show is *served*: it is added on the server first, and the JSON
  under `core/src/main/resources/i18n/` is regenerated with `scripts/update-strings.sh`, never edited by
  hand. A word about the app or the device is *native*, in `values`, `values-fr` and `values-es`, all three
  in the same commit.
* The fonts in `app/src/main/res/font/` are copies from the Splouch repo: refresh them from there, never
  edit them.

## Commits and publishing

* Commit titles read `[Topic] What changed`, one topic per commit. Areas in use: `[Board]`, `[Schedule]`, `[Picker]`, `[Filter]`, `[Sheets]`, `[Shell]`, `[Theme]`,
  `[Prefs]`, `[Empty]`, `[Servers]`, `[Icon]`, `[A11y]`, `[Core]`, `[App]`, `[Build]`, `[Docs]`; the title
  reads as a sentence.
* **Never commit, push, tag, publish a release, or open an issue or pull request unless asked.** Approval for
  one of these is not approval for the next.
* Releases go through `scripts/release.sh` (see `docs/play-store.md`): it tags the release and moves
  `appVersionName` to the next version. `main` is always one version ahead; don't tag by hand.
* `main` is protected against force-pushes and deletion: never rewrite history that is already pushed.
* Never post comments on issues or pull requests on the user's behalf without their review.

## AI policy

This project follows its [AI policy](AI_POLICY.md): a person reviews and understands every change, no
autonomous contributions, and nothing is posted on someone's behalf without their review.
