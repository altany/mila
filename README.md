# Mila (Μίλα)

Greek voice commands for Android Auto. Google Assistant and Gemini don't
understand Greek in the car; Mila does. Open it on the car screen, it listens
immediately — say an address to start Google Maps navigation, or a contact
name to place a call.

A personal tool with no backend and no analytics.

**It has to be installed from the Play Store to work in a car.** Android Auto
does not show sideloaded car apps on a real head unit — it never even looks at
them — and the "Unknown sources" developer setting doesn't change that. A
sideloaded build works fine on the desktop emulator, which is what makes this
so easy to get wrong. A private internal testing track is enough; a public
listing isn't needed. See [Installing](#install-on-the-phone).

## How it works in the car

Open Mila on the car screen. It starts listening immediately (a short tone
tells you the mic is open) — no mic tap.

- **Πλοήγηση (Navigate)** — say a destination. Mila hands it to Google Maps
  and navigation starts.
- **Κλήση (Call)** — say a contact name. If one contact clearly wins, it
  dials. If several are close, it shows a short pick list instead of guessing.

You can also just say what you want and skip the buttons: an opening verb
settles it either way, so "**κάλεσε** τον Δημήτρη" dials even when Navigate is
selected, and "**πήγαινε** στην Πάτρα" navigates. Recognised verbs are κάλεσε,
πάρε, τηλεφώνησε for calls and πήγαινε, πλοήγηση, οδήγησε, πάμε for
navigation; the verb and its article are stripped before matching.

Two big buttons switch modes, and switching restarts listening. If the
recognizer gives up early, the screen keeps whatever it heard and offers
**Ξαναπές το** (retry) or using the partial text.

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
  The car screen detects missing permissions and says to finish setup on the
  phone.

### Contact matching

Greek speech has to match contacts saved in Greek, in Greeklish, or in Latin.
Both the spoken name and each contact name are reduced to a canonical Latin
"phonetic key" (`GreekText.canonicalKey`): accents and case are stripped, and
Greek digraphs collapse to sounds (`ου`→u, `μπ`→b, `αι`→e, `ει`/`οι`/`η`/`υ`→i).

Some Greeklish letters are genuinely ambiguous, so they expand into a small
set of variant keys and the best-scoring reading wins:

| Letter | Readings | Example |
| --- | --- | --- |
| `x` | χ or ξ | Xristos (Χρήστος) / Xenia (Ξένια) |
| `h` | χ or η | Hristos / Xrhstos |
| `b` | μπ or β | Babis (Μπάμπης) / Basilis (Βασίλης) |
| `u` | ου or υ | Loukas (Λουκάς) / Kuriakos (Κυριάκος) |
| `ai` `ei` `oi` | digraph or two vowels | Kaiti (Καίτη) / Mixail (Μιχαήλ) |

Scoring is Levenshtein similarity, computed both over the whole name and token
by token, so saying just "Ελένη" matches "Ελένη Βασιλείου". A candidate must
clear a minimum score to be offered at all, and must beat the runner-up by a
margin to be dialled without asking — otherwise the pick list appears.

Matching happens in memory, not in a SQL `LIKE` query, because the contacts
provider can't compare Greek speech to Greeklish spellings. Contacts are read
off the main thread and cached when the screen opens.

### Why `SpeechRecognizer` and not `CarAudioRecord`

`CarAudioRecord` (car app library, API level 5+) records raw PCM from the car
microphone — it does **no** speech-to-text. Turning that audio into Greek text
would require either a cloud STT API (backend, cost, privacy) or a bundled
on-device model (size, complexity). Android's `SpeechRecognizer` does full
Greek recognition through the phone's Google recognition service, and the
proven open-source reference for this approach in real cars is
[aa-speech-to-text](https://gitlab.com/ron.gr/aa-speech-to-text) (GPL-3.0),
which this project used as a behavior reference.

Note that this means Mila listens through the **phone's** microphone, not the
car's. Android Auto's own Assistant uses the car microphone, so it still hears
you with the phone in a bag; Mila will not.

**Greek needs a data connection.** Google ships no on-device (offline) speech
model for `el-GR` — the phone's own language-pack list contains no Greek at
all, and the recognizer reports `Failed to get language pack of required
locale` and falls back to the network recognizer. There is no setting to
change this; it applies to any app using Android's `SpeechRecognizer`. Expect
recognition to fail without signal, which is what the retry action is for.

### Knowing when to stop listening

The recognizer decides for itself when speech has ended and treats the
`EXTRA_SPEECH_INPUT_*` timeouts as hints. A car cabin is never silent, so left
alone it holds the microphone until its own 60-second server cap — the driver
speaks and nothing happens for a minute, with separate attempts accumulating
into one transcript. Mila therefore does its own endpointing: once words start
arriving, a pause with no new partial results ends the turn, and a hard ceiling
backstops it. Erring the other way is just as bad: too short a timeout cuts the
driver off mid-address.

### Trusting more than the first guess

Greek command verbs are easy to mishear — the recognizer has returned
"θάλασσα" for "κάλεσε", which quietly turned a phone call into directions to a
beach bar. Three hypotheses are requested and all of them are used: the intent
comes from the first alternative that opens with a real command verb, and
contact matching scores every alternative and keeps each contact's best, so a
name mangled in the top transcription can still be found in another reading.
Debug builds log the hypotheses under the `Mila` tag.

## Using it in another language

Nothing here is Greek by necessity — the problem is that Android Auto supports
a fixed list of languages and yours may not be on it. Forking this for another
language means changing four things:

1. **The recognition locale.** `SpeechController.RECOGNITION_LOCALE` is
   `"el-GR"`. Set it to yours, and check Android's recognizer actually supports
   it — the list is not the same as the one Android Auto supports, which is the
   whole reason this app exists.
2. **The command words.** `SpokenCommand` holds `callVerbs`, `navVerbs` and
   `fillers` — the words that mean "call", "navigate", and the articles that
   get stripped before matching. Replace them with your language's. Note the
   words are normalized before comparison, so write them the way
   `GreekText.normalize` would leave them.
3. **The name matching.** `GreekText` maps Greek to a Latin "phonetic key" so
   that a name spoken in Greek matches a contact saved in Latin letters. If
   your language already uses the Latin alphabet, you probably don't need any
   of this: strip accents and case, and compare with `similarity` directly.
   If it uses another script, you need the equivalent mapping — and the
   interesting part is the ambiguity handling, where one letter has several
   plausible readings and each expands into variants.
4. **The interface text.** `app/src/main/res/values/strings.xml`.

The parts worth keeping as-is are the ones that took the longest to get right
and aren't language-specific: ending the listening turn yourself rather than
trusting the recognizer, and using every recognition hypothesis instead of the
top one.

One thing that isn't optional: **the app has to be installed from the Play
Store to appear in a real car.** A sideloaded build works on the emulator and
is invisible in the car. An internal testing track is enough — see below.

## Build

Requirements: JDK 17+, Android SDK (platform 36). A `local.properties` with
`sdk.dir` pointing at the SDK (Android Studio creates this automatically).

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # unit tests (transliteration + matching)
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
   Desktop Head Unit Emulator**. From the command line instead:

   ```bash
   ~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "extras;google;auto"
   ```

   On first run you may need `chmod +x` on the `desktop-head-unit` binary.
2. On the phone: Android Auto Developer settings → **Start head unit server**.
3. Connect the phone by USB and forward the port, then run the DHU:

```bash
adb forward tcp:5277 tcp:5277
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

The car screen appears in a desktop window with the phone providing the apps.

**Stop the head unit server before driving.** Android Auto projects one session
at a time, so a server left running keeps the phone busy serving the DHU and
the app can be missing from a real car's launcher. Use Android Auto's overflow
menu → **Διακοπή διακ. κεντρ. μονάδας** (stop head unit server) before plugging
into the car. Take care not to hit **Παύση λειτ. προγραμματιστή** (pause
developer mode) next to it — that hides every sideloaded app with no other
symptom.

## CI / releases

GitHub Actions ([build.yml](.github/workflows/build.yml)):

- **Push to `main`** → tests run and a debug APK is uploaded as a workflow
  artifact.
- **Tag `v*`** → tests run, then the signed APK and app bundle are attached to
  a GitHub Release **and the bundle is published to Play's internal testing
  track**. The build reaches the phone as an ordinary app update.

Release in one step:

```bash
scripts/release.sh          # patch bump
scripts/release.sh 0.2.0    # explicit version
```

That means a change can go from an edit to running in the car without a
laptop: push a tag from anywhere, and Play delivers it.

### Publishing to Play

The app **must** be installed from Play to appear in a real car, so the
release job publishes automatically. Two small scripts do it:

- [`scripts/play-token.sh`](scripts/play-token.sh) swaps the service account
  key for a short-lived token using the standard JWT-bearer flow. Done directly
  rather than through a generic auth action, which mints tokens by
  impersonating the service account and needs the IAM Credentials API plus a
  token-creator role granted to the account on itself.
- [`scripts/publish-play.sh`](scripts/publish-play.sh) makes four REST calls —
  open an edit, upload the bundle, point the internal track at it, commit —
  retrying on 5xx, because the Play API returns 503 often enough that one
  attempt isn't a fair test.

No third-party action handles the credential. Setting this up needs a Google
Cloud service account with the **Google Play Android Developer API** enabled,
invited into Play Console under Users and permissions with only *Release apps
to testing tracks* and *View app information*. Its JSON key goes in the
`PLAY_SERVICE_ACCOUNT_JSON` secret. Scoped that way, the worst a leaked key
could do is push a build to your own test track.

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
