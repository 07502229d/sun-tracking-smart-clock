# Sun Tracking Smart Clock

A single-file web app (also packaged as an Android APK) that computes live solar
data for any location — no server, all calculations run in the browser/WebView.

- **Web preview:** https://sun-tracking-smart-clock.netlify.app
- **Android app:** package `hk.suntracking.clock` — download the APK from Releases

## Features

- **Six sun screens:** 1 Solar Overview (full-day altitude curve + next-event
  countdown), 2 Daylight Details (day segments, five key times, declination +
  apparent right ascension), 3 Twilight (civil / nautical / astronomical, start,
  end and duration), 4 Altitude side view, 5 Azimuth compass, 6 interactive 3D
  sky dome (Three.js with 2D fallback).
- **39 world cities** grouped by region in one dropdown, each with its real
  IANA timezone (daylight-saving resolved per date); GPS location; manual
  latitude/longitude entry.
- **Trilingual UI:** 繁體中文（香港）/ 简体中文 / English — the whole interface
  shows one language at a time.
- **24-hour time** everywhere, with a live UTC clock and the selected
  location's timezone name + UTC offset.
- **Annual PDF report** (preview first, then save): one merged chronological
  table of 36 rows — the 15th of each month plus all 24 solar terms
  (astronomical definition, ecliptic longitude solved per term) with sunrise,
  solar noon, sunset, day length, noon altitude, declination, right ascension
  and equation of time.
- **CSV export** (preview first, then download): the same 36-row dataset as a
  machine-clean CSV (UTF-8 BOM, English headers, location/coords/timezone on
  every row) for Excel, or for Revit via its built-in Dynamo.
- **Weather card** that follows the selected location: Open-Meteo primary,
  wttr.in fallback, source always labelled; shows "unavailable" honestly if
  both fail — never fabricated.

## Files in this repo

| File | What it is |
|---|---|
| `index.html` | The complete web app as one self-contained file (obfuscated release build — see the version history). Open it in any browser. |
| `sun-tracking-smart-clock-v1.5.apk` | Android APK (WebView shell + the same `index.html` embedded). |
| `android/` | The APK build project (`build_apk.py`, `AndroidManifest.xml`, `src/MainActivity.java`). |

## Install the APK

1. Download `sun-tracking-smart-clock-v1.5.apk` (from this repo or the
   v1.5 Release) to an Android 8.0+ phone.
2. Open the file and allow "install from unknown sources" for the app you
   opened it with, if Android asks.
3. In the app: GPS button asks for location permission; CSV/PDF saves are
   written to the phone's **Download** folder by the shell's native bridge
   (`SunBridge.saveFile`), with a confirmation toast.

## Rebuild the APK

Requires JDK 17 and Android SDK build-tools 34 (paths are set at the top of
`android/build_apk.py`):

```sh
python3 android/build_apk.py
```

The script embeds the current exported `index.html`, compiles the WebView
shell with aapt2/javac/d8 (no Gradle), and signs the APK.

> **Signing:** release builds are signed with a keystore that is kept
> **offline and is NOT in this repository**. A locally generated keystore
> will produce an APK that cannot upgrade an installed release build in
> place — uninstall first in that case.

## Version history

- **v1.5** (versionCode 6) — PDF save regression fixed. The previous
  version's new save path (a separate jsPDF library loaded from a CDN,
  plus a hand-built PDF document) silently failed in the phone's
  WebView — the preview opened, but no file was saved. The save path
  now uses **only** the html2pdf bundle — the exact machinery that
  saved successfully on-device in v1.3 — and the one-page A3 result is
  achieved inside it: the report is captured once, staged into a
  fixed-size fit box, and emitted as a single A3 landscape page. Two
  safety nets were added: any save failure now shows the real error in
  the status line ("Save failed: …") instead of failing silently, and
  if the one-page generation throws, the save automatically retries
  once with the plain v1.3-proven settings (multi-page if necessary)
  and says so in the status line. Footer version line updated to
  "Sun Tracking Clock V1.5".
- **v1.4** (versionCode 5) — Annual report PDF now fits exactly one page:
  the whole report sheet (title block, the full 36-row merged table and
  the footnotes) is rendered as a single image and fitted — aspect ratio
  preserved, centred — into one A3 landscape page. This replaces
  html2pdf's automatic pagination, which had split the table across
  pages and stranded the footnotes on a page of their own (three pages
  in total). Footer version line updated to "Sun Tracking Clock V1.4".
- **v1.3** (versionCode 4) — Release build: the published copy of the web
  app (this repo's `index.html`, the Netlify deploy and the APK's embedded
  page) is now obfuscated with the same pipeline used for the Liu Shu apps
  (javascript-obfuscator: base64 string array, hexadecimal identifiers,
  control-flow flattening; globals and object keys untouched, so the
  `SunBridge` native bridge and the Three.js guard behave identically —
  verified by an A/B run against the readable build). The source stays
  readable in the Muse app; only the published copy is obfuscated.
  Footer version line updated to "Sun Tracking Clock V1.3".
- **v1.2** (versionCode 3) — Legal block added at the end of the page:
  acknowledgements, disclaimer and copyright statement, in all three UI
  languages; the footer version line now tracks the latest release
  version ("Sun Tracking Clock V1.2"); GitHub repo About description
  corrected and the README web-preview / Android lines split into two.
- **v1.1** (versionCode 2) — GPS permission-flow fix in the shell (the
  geolocation request is now held until the system permission result
  returns, so the first tap works); page geolocation errors split into
  permission / timeout / unavailable with a 15 s timeout; English
  letter-spacing tightened (Chinese unchanged); footer line added
  ("Sun Tracking Clock V1.0"); CSV/PDF native save bridge via
  `SunBridge.saveFile`.
- **v1.0** (versionCode 1) — first APK build: WebView shell with the full
  six-screen app embedded, GPS, SunBridge save bridge, custom sun icon.
