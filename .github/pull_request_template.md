<!--
Thanks for this. CONTRIBUTING.md has the setup, the conventions, and the reasoning
behind the ones that look arbitrary: https://github.com/olivierouellet/Splouch-android/blob/main/CONTRIBUTING.md
-->

## What this changes

<!-- One or two sentences. The *why* matters more than the diff — that is where this
     tree keeps its reasoning. -->

## Checks

```sh
./gradlew :core:test
./gradlew :app:assembleDebug
```

- [ ] Both green. (`:core:test` never compiles `:app` — assemble it too, or a Compose change can pass without being built.)
- [ ] `LiveServerTests` run against a real server, if this touches `SplouchApi`, `SplouchSocket` or `MeetSession`. They return early and report green without `SPLOUCH_LIVE_SERVER`.
- [ ] `parity.md` updated — the rows this touches say `done`, `deferred` or `diverges`, and a `diverges` row says what the app does instead and why.
- [ ] No wire field invented: every payload key read here is in `api.md`.
- [ ] No `android.*`, Compose or OkHttp import added to `:core`.
- [ ] Any new dependency is in `gradle/libs.versions.toml`, and the PR says why it earns its place.
- [ ] No formatter run over the tree.

## Seen on a screen

<!-- Which device or AVD, and what you looked at. "Doesn't touch the UI" is a complete
     answer. -->

- [ ] Installed and looked at.
- [ ] Both orientations, if the layout moved.
- [ ] Doesn't touch the UI.

## Strings

- [ ] Doesn't add a user-visible string.
- [ ] Served string — added on the server first, then captured with `scripts/update-strings.sh`. The snapshot under `core/src/main/resources/i18n/` was not edited by hand.
- [ ] Native string — in `app/src/main/res/values/strings.xml`, with `values-fr` and `values-es`.

## Also

- [ ] `README.md` / `emulator.md` updated, if this changes something a developer runs.
- [ ] Commits read `[Area] What changed`, one topic each.
- [ ] Accessibility held up: font scale at 2.0×, and TalkBack order and labels, for any screen this touches.

<!-- Closes #NNN -->
