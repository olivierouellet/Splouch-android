# Running the app on an emulator

A debug build on an AVD, driven from the shell. Everything here is `adb` and the
emulator binary — no Studio needed once an AVD exists.

## One-time setup

Neither `emulator` nor `adb` is on the PATH after a Studio install. Add them:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
```

The rest of this file assumes that; without it, spell out
`~/Library/Android/sdk/emulator/emulator` and `~/Library/Android/sdk/platform-tools/adb`.

Creating an AVD needs Studio's Device Manager. `avdmanager` ships in `cmdline-tools`,
which a default Studio install does not lay down — check with
`ls "$ANDROID_HOME/cmdline-tools"` before reaching for it.

## Start it

```sh
emulator -list-avds                 # the names -avd accepts
emulator -avd Medium_Phone          # holds the terminal, logs to it
```

To get the prompt back:

```sh
nohup emulator -avd Medium_Phone >/dev/null 2>&1 &
```

**`adb wait-for-device` returns too early.** It unblocks when the device attaches,
which is long before Android is up; an `install` or `am start` in that window fails
or hangs. Wait for the boot flag instead:

```sh
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
```

## Install and launch

```sh
./gradlew :app:installDebug
adb shell am start -n app.splouch.android/.MainActivity --es server http://10.0.2.2:5055
```

`--es server` points the app at one dev server for this launch without touching stored
preferences, so it does not disturb whatever the app was last pointed at.

**`10.0.2.2` is the emulator's alias for the host's loopback.** Inside the emulator
`127.0.0.1` is the emulator itself, so a server on your Mac is unreachable at that
address. Only `.local` names, `localhost`, `127.0.0.1` and `10.0.2.2` may be dialled
over plain HTTP — see `app/src/main/res/xml/network_security_config.xml` and app.md
`P-12`.

Dev servers, per the README. Note the Pi cannot have port 5000 on macOS: ControlCenter
(AirPlay Receiver) listens there, so pass an explicit port.

```sh
cd ../Splouch/server && uv run uvicorn app:app --port 5056                      # Pi
cd ../Splouch/cloud  && DATA_DIR=/tmp/splouch-cloud uv run uvicorn cloud_server:app --port 5055
```

## Scanning a QR code without a camera (`P-16`)

The App Link a code carries is `https://splouch.ca/add?server=<origin>`. Deliver that
Intent by hand, naming the package so it goes to the app whether or not the link has
been verified:

```sh
adb shell am start -a android.intent.action.VIEW \
  -d "'https://splouch.ca/add?server=http%3A%2F%2F10.0.2.2%3A5056'" app.splouch.android
```

Quote the URL twice — the outer quotes are the Mac's shell, the inner ones the device's,
and without them `&` and `?` are eaten before `am` ever sees them.

That is the same Intent a verified link produces, minus the one thing it cannot test:
whether Android decides to send it to the app at all rather than offering a chooser.
That decision needs `https://splouch.ca/.well-known/assetlinks.json` and shows here:

```sh
adb shell pm get-app-links app.splouch.android   # splouch.ca: verified, or 1024 for no response
adb shell pm verify-app-links --re-verify app.splouch.android
```

A debug build is signed with the debug key, so it will never verify against an
`assetlinks.json` listing the release certificate. To try the real path end to end,
either add the debug fingerprint (`keytool -list -v -keystore ~/.android/debug.keystore
-alias androiddebugkey -storepass android`) as a second entry in the file, or install a
release-signed build.

## Driving it from the shell

```sh
adb shell input tap 540 728                 # device pixels, not the scaled window
adb shell input text "elise"                # no spaces; use %s for each one
adb shell input keyevent KEYCODE_BACK
adb exec-out screencap -p > shot.png        # exec-out, not shell: no CRLF mangling
```

Tap coordinates are in the device's own pixel space (1080×2400 on `Medium_Phone`). If
you are reading positions off a scaled screenshot, multiply by the scale factor first.

## Shut down

```sh
adb emu kill
```

## When the screen looks wrong

Check these before suspecting the code — each produced a convincing false alarm during
the `S-09` work.

- **A stale APK.** `installDebug` is the only thing that updates the device; a Gradle
  build alone does not. Compare what is installed against your edits:

  ```sh
  adb shell dumpsys package app.splouch.android | grep lastUpdateTime
  ```

- **An empty start list.** A meet with no heats renders an empty Schedule tab, offers no
  search suggestions, and so accepts no filters — which looks exactly like broken
  filtering. Check the server, not the app:

  ```sh
  curl -s http://127.0.0.1:5056/schedule.json | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["heats"]), "heats")'
  curl -s http://127.0.0.1:5055/meets        # cloud: ids for /meet/{id}/schedule
  ```

  On the Pi, `POST /test_play {"name":"…​.cts"}` plays a recording into an empty meet.

- **The wrong server.** With no `--es server`, the app opens whatever it has stored,
  falling back to the default cloud — not your local one. The shell shows the address
  above the tabs whenever it is not the default.

- **Language.** The UI language is the stored preference, then the meet's, then the
  device's (app.md `T-06`), so the chrome may not come up in English.
