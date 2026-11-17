# Sun Tracking Smart Clock

A single-file web app (also packaged as an Android APK) that computes live solar
data for any location — no server, all calculations run in the browser/WebView.

**Web preview:** https://sun-tracking-smart-clock.netlify.app
**Android package:** `hk.suntracking.clock`

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
| `index.html` | The complete web app as one self-contained file. Open it in any browser. |
| `sun-tracking-smart-clock-v1.1.apk` | Android APK (WebView shell + the same `index.html` embedded). |
| `android/` | The APK build project (`build_apk.py`, `AndroidManifest.xml`, `src/MainActivity.java`). |

## Install the APK

1. Download `sun-tracking-smart-clock-v1.1.apk` (from this repo or the
   v1.1 Release) to an Android 8.0+ phone.
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

- **v1.1** (versionCode 2) — GPS permission-flow fix in the shell (the
  geolocation request is now held until the system permission result
  returns, so the first tap works); page geolocation errors split into
  permission / timeout / unavailable with a 15 s timeout; English
  letter-spacing tightened (Chinese unchanged); footer line added
  ("Sun Tracking Clock V1.0"); CSV/PDF native save bridge via
  `SunBridge.saveFile`.
- **v1.0** (versionCode 1) — first APK build: WebView shell with the full
  six-screen app embedded, GPS, SunBridge save bridge, custom sun icon.
