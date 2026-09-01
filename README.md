# Mila (Μίλα)

Greek voice commands for Android Auto. Google Assistant and Gemini don't
understand Greek in the car; Mila does. Open it on the car screen, it listens
immediately — say an address to start Google Maps navigation, or a contact
name to place a call.

This is a personal tool, installed from a locally or CI-built APK. No Play
Store, no backend, no analytics.

## Status

Work in progress. Phase 1 (scaffold + CI) done.

## Architecture (one page)

- **Android Auto projection app** built on `androidx.car.app` (the Android for
  Cars App Library), category `POI` so it appears on the Android Auto launcher.
  This is *not* an Android Automotive OS app — it runs on the phone and
  projects templates to the car screen.
- `MilaCarAppService` → `MilaSession` → screens built from car app templates.
- **Speech recognition** runs on the phone via Android's `SpeechRecognizer`
  with locale `el-GR` (see reasoning below).
- **Navigate mode** hands the recognized text to Google Maps with a
  `google.navigation:q=` intent (fallback `geo:0,0?q=`) via
  `CarContext.startCarApp`, so Maps takes over the car screen.
- **Call mode** matches the recognized name against phone contacts —
  accent/case-insensitive, Greeklish-aware via a Greek→Latin transliteration
  layer, fuzzy with a confidence threshold — then places the call with
  `ACTION_CALL`.
- Runtime permissions can't be granted on the car screen, so a one-time
  phone-side `SetupActivity` requests mic, contacts and call permissions.

### Why `SpeechRecognizer` and not `CarAudioRecord`

`CarAudioRecord` (car app library, API level 5+) records raw PCM from the car
microphone — it does **no** speech-to-text. Turning that audio into Greek text
would require either a cloud STT API (backend, cost, privacy) or a bundled
on-device model (size, complexity). Android's `SpeechRecognizer` does full
Greek recognition through the phone's Google recognition service, and the
proven open-source reference for this approach in real cars is
[aa-speech-to-text](https://gitlab.com/ron.gr/aa-speech-to-text) (GPL-3.0),
which this project used as a behavior reference. In practice, when the phone
is connected to the car, the phone picks up in-cabin speech fine.

## Build

Requirements: JDK 17+, Android SDK (platform 36). A `local.properties` with
`sdk.dir` pointing at the SDK (Android Studio creates this automatically).

```bash
./gradlew assembleDebug          # debug APK
./scripts/build-release.sh       # signed release APK (creates keystore on first run)
```

## Install on the phone

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or download the APK from the GitHub Releases page in the phone's browser and
open it (allow "install unknown apps" for the browser when prompted).

## Enable the app in Android Auto

Sideloaded car apps are hidden until developer mode is on:

1. Open the Android Auto app on the phone (in phone Settings → search
   "Android Auto").
2. Tap the version number ~10 times to enable Developer settings.
3. Overflow menu → Developer settings → enable **Unknown sources**.
4. Restart Android Auto (or replug the phone). Mila appears in the car
   launcher.

## Test on the Desktop Head Unit (DHU) without a car

1. Install the DHU: Android Studio → SDK Manager → SDK Tools → **Android Auto
   Desktop Head Unit Emulator**.
2. On the phone: Android Auto Developer settings → **Start head unit server**.
3. Connect the phone by USB and forward the port, then run the DHU:

```bash
adb forward tcp:5277 tcp:5277
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

The car screen appears in a desktop window with the phone providing the apps.

## CI / releases

GitHub Actions ([build.yml](.github/workflows/build.yml)):

- **Push to `main`** → debug APK uploaded as a workflow artifact.
- **Tag `v*`** → signed release APK attached to a GitHub Release. That
  Releases page is the delivery channel: open it on the phone, download,
  install.

Release in one step (bumps version, tags, pushes; CI does the rest):

```bash
scripts/release.sh          # patch bump
scripts/release.sh 0.2.0    # explicit version
```

### Signing

Local and CI builds sign with the **same key**, so new versions install over
old ones. `scripts/make-keystore.sh` generates `keystore/mila.jks` +
`keystore.properties` once (both gitignored). CI gets the same key via these
GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `MILA_KEYSTORE_BASE64` | `base64 -i keystore/mila.jks` |
| `MILA_KEYSTORE_PASSWORD` | `storePassword` from keystore.properties |
| `MILA_KEY_ALIAS` | `mila` |
| `MILA_KEY_PASSWORD` | `keyPassword` from keystore.properties |

## License

GPL-3.0 — see [LICENSE](LICENSE). Behavior informed by
[aa-speech-to-text](https://gitlab.com/ron.gr/aa-speech-to-text) by ron.gr
(GPL-3.0); no code was copied.
