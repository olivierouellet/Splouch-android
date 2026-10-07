# Screenshots

Every screen in English and French, light and dark, on a Pixel-8-sized phone
(1080 × 2400, the `Medium_Phone` AVD in [`emulator.md`](../emulator.md)), and the
Google Play set made from them.

```text
phone/<lang>/<light|dark>/1-scoreboard.png
                          2-results.png
                          3-schedule.png
PlayStore/<en-CA|fr-CA>/feature-graphic.png    1024 × 500
PlayStore/<en-CA|fr-CA>/phone/1.png … 4.png    1080 × 1920, framed and captioned
```

## The Play Store set

Rendered by [`scripts/render-store-images.sh`](../scripts/render-store-images.sh) from
`phone/` and the icon SVG — never edited by hand. It needs Chrome. Captions and
taglines live in the script. The listing icon is not here: it is
[`app/icon/play-store-512.png`](../app/icon/play-store-512.png), see
[`app/icon/README.md`](../app/icon/README.md).

**Why the Play screenshots are framed.** Play rejects a screenshot whose long side is
more than twice its short side, and the raw captures are 20:9 (2400/1080 = 2.22). They
are set into 9:16 with a caption instead of being cropped, which would cut off the tab
bar.

**No alpha.** Play's feature graphic and screenshots must be JPEG or 24-bit PNG; Chrome
writes 24-bit for an opaque page. Tablet screenshots are optional and not made.

## What is on them

**Every name is invented.** `store-shots.lxf` is a six-event meet, *Coupe Splouch 2026*,
built from the athletes of the Pi's authored `200m_medley_2heats` recording (event 3), so
the board fills from that recording and the schedule has more than one event. Never
retake these against a real meet: the people on a start list are mostly minors.

The scoreboard is the finish of event 3 heat 1, results that heat, schedule the screen
as it opens during heat 2.

## Recapturing

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
   Heat 1 finishes about 150 s in and holds until heat 2 starts, about 210 s in; the
   board wipes when the recording ends. Shoot the schedule after heat 2 starts, or it
   opens on heat 1.
4. Language and appearance come from the app's own preferences, so a debug build's
   `shared_prefs/splouch.prefs.xml` is rewritten with `run-as` before each launch
   (`lang` `en`/`fr`, `appearance` `LIGHT`/`DARK`, `tab`, `server`), with
   `cmd locale set-app-locales` and `cmd uimode night` set to match.
5. Status bar: SystemUI demo mode (`clock -e hhmm 1000`, full battery, Wi-Fi, no
   notifications).

A dark launch sits on the splash and the connecting screen longer than a light one; give it
about 8 s before `screencap`.
