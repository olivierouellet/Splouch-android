# Screenshots

The app on a Pixel-8-sized AVD (1080×2400, `Medium_Phone` in [`emulator.md`](../emulator.md)),
in both languages and both appearances:

| | Light | Dark |
| --- | --- | --- |
| English | [`en/light/`](en/light) | [`en/dark/`](en/dark) |
| Français | [`fr/light/`](fr/light) | [`fr/dark/`](fr/dark) |

Each folder holds `1-scoreboard.png`, `2-results.png` and `3-schedule.png`. The Play
listing's framed versions are made from these — see [`../store/`](../store).

## What is on them

**Every name is invented.** `store-shots.lxf` is a six-event meet, *Coupe Splouch 2026*,
built from the athletes of the Pi's authored `200m_medley_2heats` recording (event 3), so
the board fills from that recording and the schedule has more than one event. Never
retake these against a real meet: the people on a start list are mostly minors.

The scoreboard is the finish of event 3 heat 1, results that heat, schedule the screen
as it opens during heat 2.

## Retaking them

1. Run a Pi server with its own data folder, so a dev `~/SplouchData` is left alone,
   and name it — that name is the board's title:
   ```sh
   cd ../Splouch/server && HOME=/tmp/shots uv run uvicorn app:app --host 0.0.0.0 --port 5057
   ```
   Set `"meet_title": "Coupe Splouch 2026"` in `/tmp/shots/SplouchData/settings.json`
   and restart it.
2. Copy `store-shots.lxf` into `/tmp/shots/SplouchData/recorded/`, beside a copy of
   `console_recordings/200m_medley_2heats.serial` named `store-shots.serial`. The
   `.lxf` beside a recording is the meet it loads.
3. Log in (`score`/`swimming`) and `POST /test_play {"name":"store-shots.serial"}`.
   Heat 1 finishes about 150 s in and holds until heat 2 starts; the board wipes when
   the recording ends.
4. Language and appearance come from the app's own preferences, so a debug build's
   `shared_prefs/splouch.prefs.xml` is rewritten with `run-as` before each launch
   (`lang` `en`/`fr`, `appearance` `LIGHT`/`DARK`, `tab`, `server`), with
   `cmd locale set-app-locales` and `cmd uimode night` set to match.
5. Status bar: SystemUI demo mode (`clock -e hhmm 1000`, full battery, Wi-Fi, no
   notifications).

A dark launch sits on the splash and the connecting screen longer than a light one; give it
about 8 s before `screencap`.
