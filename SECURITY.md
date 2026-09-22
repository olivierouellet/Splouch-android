# Security Policy

This is the Android spectator client. It logs nobody in, stores no credentials, and cannot
change anything on a meet: everything it does is read a server and draw it. Most of what
follows is about what it reads, what it keeps on the phone, and where it will and won't
send either.

The server, the console decoders and the cloud relay are in the
[Splouch repo](https://github.com/olivierouellet/Splouch) and have their
[own policy](https://github.com/olivierouellet/Splouch/blob/master/SECURITY.md). If the
finding is in what a server *sends*, report it there.

## Reporting a vulnerability

**Don't open a public issue.** Report privately through GitHub:

* [Report a vulnerability](https://github.com/olivierouellet/Splouch-android/security/advisories/new)
  from the repository's **Security** tab — visible only to the maintainer, or
* Contact [@olivierouellet](https://github.com/olivierouellet) directly.

Useful to include:

* Which part — the server picker and address handling, the socket loop, the string cache,
  or a screen that renders meet content.
* What the attacker has to be: on the same Wi-Fi as the phone, running a server the user
  chose to add, answering Bonjour on the local network, or in a position to answer the
  app's requests.
* A payload or a server response that shows it. A crafted `GET /i18n/{lang}` body or a
  socket frame is ideal — those and meet content are the app's untrusted input.

This is a one-maintainer project that gets most of its attention on weekends around swim
meets. Expect acknowledgement within 2 weeks; a fix takes as long as it takes, and you'll
be told where it stands. Credit in the release notes if you'd like it, and no objection to
you publishing once a fix has shipped.

---

## Supported versions

| Version | Supported |
| --- | --- |
| Latest Play Store build | ✅ Fixes land here |
| `main` | ✅ Fixes land here first |
| Any earlier build | ❌ No backports — update instead |

There is no long-term support branch. The app talks to servers over the versioned
[`api.md`](https://github.com/olivierouellet/Splouch/blob/master/docs/api.md) contract, so
an older build usually keeps working against a newer server — that is a compatibility
promise, not a security one.

---

## What Splouch for Android assumes

| | |
| --- | --- |
| **It only ever reads** | There is no login, no write endpoint, no admin surface. The app holds nothing an attacker would want to steal from it. |
| **Everything from a server is untrusted text** | Swimmer names, club names, event names and served strings come from whoever typed them into Splash, through a server this phone does not control. They are drawn as `Text` by Compose, never as markup and never as a format string. |
| **Cleartext is for the local network only** | `network_security_config.xml` denies cleartext in the base config and permits it for `local` and its subdomains — nothing else. A Pi is plain HTTP and is reached by its mDNS name; anything remote gets `https://` and the platform's normal rules. Android matches host *names*, not address ranges, so a Pi is never dialled by a raw IP, and `ServerAddress` enforces the same rule before a request is made. The debug overlay adds `localhost`, `127.0.0.1` and `10.0.2.2` for the dev servers, and ships in debug builds only. A remote server that speaks only HTTP will fail to connect, and that is the intended outcome (`app.md` `P-12`). |
| **Two permissions, and neither is a sensor** | `INTERNET` and `ACCESS_NETWORK_STATE`. Nearby servers come from `NsdManager` on `_splouch._tcp`, which needs no location permission — the app never asks for one, and never asks for storage, camera or notifications either. |
| **The attendance id identifies nobody** | `vid` is a random `UUID`, generated **per server origin** on first use and stored in the `splouch.vid` preferences file. It is never derived from the device — no `ANDROID_ID`, no advertising id — and never shared between servers, so two servers cannot correlate a phone. The server counts distinct ids and nothing else. |
| **Nothing leaves the phone but what a server needs** | The app sends the meet it is joining and its `vid`, to the server the user chose. There is no analytics SDK, no crash reporter and no telemetry of any kind: the dependency list is Compose, AndroidX, coroutines, kotlinx-serialization and OkHttp, and it is in `gradle/libs.versions.toml` in full. |
| **What is cached is only what was served** | The string bundle is the body of `GET /i18n/{lang}` per server and language, written to the app's private `files/i18n/` with its ETag and revalidated. The server address, the saved servers, the language, the label style, the appearance and the last tab are in `splouch.prefs`. None of it is secret, and all of it goes with the app's data. |
| **Adding a server is a deliberate act** | Servers come from the bundled default, from Bonjour on the current network, from `GET /servers` on the server already chosen, or from an address the user types — and a typed one is checked with `GET /server` before it is saved. Whichever way one is offered, connecting to it is the reader's choice and the cleartext rule still applies to it. |

### Out of scope

* Physical access to an unlocked phone. Everything the app stores is in its own private
  data directory, readable there by design, and none of it is a credential.
* A malicious server the user chose to add showing false or offensive meet content. Bad
  data drawn as data is a bad meet, not a vulnerability. Bad data that *escapes* being
  data — a crash, a hang, a read outside the app's own state — is very much in scope.
* Another device on the pool-deck LAN watching the app's traffic to a Pi. That traffic is
  cleartext on purpose (the Pi has no certificate and no internet), and it carries only
  what the scoreboard on the wall is already showing the room.
* A device on the local network answering `_splouch._tcp` with a server of its own. Bonjour
  is unauthenticated by nature; the app offers what it finds and the reader picks. A
  *typed* address is still checked, and nothing is connected to without a tap.
* Anything the server does with what it is sent. That is the
  [server's policy](https://github.com/olivierouellet/Splouch/blob/master/SECURITY.md).
* Rooted devices, builds installed from outside the Play Store, and anything repackaged
  after signing.

---

## Hardening your install

There is little to configure — which is the point — but:

1. **Use `https://` for anything not on the local network.** The app defaults to it for a
   typed hostname and will refuse the cleartext one; don't talk it out of that.
2. **Only add servers you were given by the meet's organizer.** A server you add sees the
   meets you open on it.
3. **Keep the app current**, and on the same generation as the server you follow most.
4. **Clear storage, or uninstall, to clear everything.** There is no separate reset: the
   address, the preferences, the `vid` and the cached strings all live in the app's own
   data directory.
