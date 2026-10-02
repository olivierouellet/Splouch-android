# Play Store submission

Everything the Play Console asks for, with the value to enter. **TODO** marks what is
still open; the [checklist](#before-the-first-upload) at the end is the order to do
it in.

## Account

| Field | Value |
| --- | --- |
| Account type | Personal |
| Identity verification | Done in the Console; the developer name and address may be shown publicly |
| Testing requirement | Personal accounts created after Nov 2023 must run a **closed test with at least 12 testers opted in for 14 consecutive days** before production access |

## Create app

| Field | Value |
| --- | --- |
| App name | Splouch |
| Default language | French (Canada) – fr-CA |
| App or game | App |
| Free or paid | Free |
| Declarations | Developer Program Policies, US export laws: accept |

## Store listing

Both languages: fr-CA (default) and en-CA.

| Field | Value |
| --- | --- |
| App name (≤ 30) | Splouch |
| Short description (≤ 80) | [Drafts](#descriptions) |
| Full description (≤ 4000) | [Drafts](#descriptions) |
| App icon, 512 × 512 | [`app/icon/play-store-512.png`](../app/icon/play-store-512.png) |
| Feature graphic, 1024 × 500 | `Screenshots/PlayStore/<lang>/feature-graphic.png` |
| Phone screenshots | `Screenshots/PlayStore/<lang>/phone/1.png` … `4.png` |
| Tablet screenshots | None (optional) |
| Category | Sports |
| Tags | Up to 5 from Play's list, chosen in the Console |
| Contact email | **TODO** a dedicated address |
| Website | <https://splouch.ca> |

How the store images are made: [`Screenshots/README.md`](../Screenshots/README.md).

### Descriptions

Drafts. The full description stays within what the app does today.

#### fr-CA

Short (≤ 80):

```text
Suivez les compétitions de natation en direct : tableau, résultats et horaire.
```

Full:

```text
Splouch met le tableau d'affichage de la piscine dans votre poche.

Choisissez une compétition dans la liste, ou connectez-vous au serveur de la piscine sur le wifi de l'installation, et suivez la course depuis les estrades :

• Tableau en direct : chronomètre de course, temps de passage et classements à mesure qu'ils arrivent
• Résultats de chaque série
• Horaire de la compétition, filtrable par nageur ou par club
• Ajout d'un serveur en scannant un code QR affiché à la piscine

L'application est offerte en français, en anglais et en espagnol, reprend le thème de chaque compétition, en clair ou en sombre, et fonctionne sur téléphone et tablette.

Les résultats affichés sont en direct et non officiels.
```

#### en-CA

Short (≤ 80):

```text
Follow swim meets live: scoreboard, results and schedule.
```

Full:

```text
Splouch puts the pool's scoreboard in your pocket.

Pick a meet from the list, or connect to the pool's own server on the venue's wifi, and follow along from the stands:

• Live scoreboard: race clock, splits and places as they land
• Results for every heat
• The meet schedule, filterable by swimmer or club
• Add a server by scanning a QR code posted at the pool

The app speaks English, French and Spanish, follows each meet's own theme in light or dark, and runs on phones and tablets.

Results shown are live and unofficial.
```

## App content

| Section | Answer |
| --- | --- |
| Privacy policy | **TODO** URL. Not live yet: `https://splouch.ca/privacy` returns 404. The page belongs in the `Splouch` repo |
| App access | All functionality available without special access; there is no login |
| Ads | No ads |
| Content rating | Questionnaire: reference/utility app. No violence, sexual content, profanity, drugs or gambling; no user-generated content, user interaction, location sharing or purchases. Expect *Everyone* / *PEGI 3* |
| Target audience | 13 and over. Not designed for children, so the Families policy does not apply |
| News app | No |
| Data safety | **No data collected or shared.** The app sends no identifiers; preferences stay on the device. Any attendance counting is anonymous and happens on the server |
| Government app | No |
| Financial features | None |
| Health | No |

## Release

| Field | Value |
| --- | --- |
| Artifact | `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Signing | **TODO** Upload keystore and a release `signingConfig`; the build currently produces an unsigned AAB |
| Play App Signing | Accept (Google holds the app signing key) |
| Track | Closed testing first, then production after 14 days with 12 testers |
| Countries | Canada only |
| Release name | 2026.10.0 (versionCode 202610000) |

Release notes, first release:

```text
<fr-CA>
Première version : tableau en direct, résultats et horaire des compétitions de natation.
</fr-CA>
<en-CA>
First release: live scoreboard, results and schedule for swim meets.
</en-CA>
```

## After the first upload

The QR-code App Link (`parity.md` `P-16`) needs splouch.ca to serve
`/.well-known/assetlinks.json` naming the **App signing** SHA-256 from the Console's
*App signing* page, not the upload key's. It currently returns 404. Until it is served,
a scanned code opens a chooser instead of the app. Once the listing is live, point the
`/add` page's store link at
`https://play.google.com/store/apps/details?id=app.splouch.android`. Both changes are
made in the `Splouch` repo.

## Before the first upload

1. **TODO** Choose the contact email.
2. **TODO** Publish the privacy policy on splouch.ca.
3. **TODO** Create the upload keystore and sign the release build.
4. Regenerate the string snapshot: `scripts/update-strings.sh https://splouch.ca`.
5. Build the AAB, create the app in the Console, and fill in the sections above.
6. Upload to closed testing and recruit 12 testers for 14 days.
7. Serve `assetlinks.json` with the App signing fingerprint.
8. Apply for production, then turn on the `/add` store link.
