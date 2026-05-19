# Philips TV Remote

Android remote for **Philips 55PUS7303** (and similar Philips Android TVs) on your home Wi‑Fi. Combines **Android TV Remote Protocol v2** (navigation, text, voice) with **Philips JointSpace** (Sources, TV, Ambilight, YouTube, TV guide).

## Features

- Network discovery (`_androidtvremote2._tcp`) and manual IP entry
- D-pad, Home, Back, volume, mute, power
- Numeric and text keyboard
- Hold-to-talk voice (PCM 8 kHz via ATvRP)
- Shortcuts: Sources, TV (cable), Ambilight, YouTube, TV Guide
- Scrollable EPG (TV API probe + XMLTV fallback)

## TV setup

On the TV: enable **Remote control** / allow mobile apps (same as the official Philips or Google TV remote).

## Build (Docker only)

No local Android SDK or Gradle install required. The project includes the Gradle wrapper under `android/`.

```bash
docker compose build
docker compose run --rm extract
```

APK output: [`out/remote-controller.apk`](out/remote-controller.apk)

Or:

```bash
docker build -t remote-controller .
docker compose run --rm extract
```

## Install on phone

This app is **not from Google Play** — it is signed with a debug key for home use. Samsung/Google may show **Play Protect** warnings such as “app not secure” or “workaround security”. That is expected for sideloaded home-network remotes: the app talks only to your TV on your Wi‑Fi (private IP) using TLS the TV provides locally. Tap **Install anyway** if you built the APK yourself from this repo.

Copy `out/remote-controller.apk` to the phone and install (enable “Install unknown apps” if prompted), or use:

```bash
adb install -r out/remote-controller.apk
```

## First run

1. Open the app → **Scan network** (or enter TV IP).
2. Select your TV.
3. **Pairing**
   - Tap **Show Philips PIN on TV** → enter the PIN from the TV.
   - Enter the **6-character hex PIN** for Android TV Remote when shown on the TV.
4. Use the remote. **TV Guide** downloads EPG (XMLTV preset or TV API if available).

## EPG sources

In TV Guide, pick an XMLTV preset (e.g. Open-EPG Netherlands) and tap **Refresh guide**. The default EPGShare URL is large; a regional Open-EPG file is often faster.

## Size

Release build targets **&lt; 10 MB** (`arm64-v8a` only, R8 + resource shrinking).

## License

MIT — see [LICENSE](LICENSE).
