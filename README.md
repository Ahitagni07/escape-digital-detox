# ESCAPE — The World Is Your Password 🌱

**A privacy-first Android digital-detox adventure powered by offline AI.**

> Want to unlock YouTube or Instagram? Put your phone in your pocket, discover something real outside, and capture a fresh nature photograph. **The world is your password.**

ESCAPE converts mindless scrolling into small outdoor quests. A local Gemma model creates new quest descriptions. Android checks movement, ML Kit examines a fresh mission photo **on-device**, and a successful quest earns a **limited** window of social-app access. Saturday and Sunday bring optional bicycle adventures and nearby green-place discovery. After dark, ESCAPE switches to safe, nature-related creative challenges indoors.

This is a **Hacktoberfest 2026 Touch Grass** project and an experimental Android prototype—not a safety device or a tamper-proof parental-control product.

## The experience

### 🌿 Weekday daylight: three clues to unlock

1. **Move:** Start a quest and take a walk/stroll. Default target: **10 minutes + 600 steps** when a step sensor is present.
2. **Discover:** Find the concrete natural subject in your current mission (grass, a tree, a flower, sky or water). Keep a safe distance from roads and water.
3. **Capture:** After the movement target, tap **Capture fresh photo & unlock**. ESCAPE launches the camera—**no gallery submissions**—and checks the captured image offline.

An approved photo grants **30 minutes of access by default** on a new installation. Previously saved reward settings are retained. Once the window expires, social apps are protected again until another quest is completed.

**Example quest:** “Walk for ten minutes, discover a striking patch of grass in a public place, notice its texture, and photograph it.”

**The proof has limits:** Android step counts can be accumulated indoors; image labels can misclassify photos. A camera-only photo is *newly captured through ESCAPE*, not cryptographic proof of the subject's location or authenticity. ESCAPE cannot verify that someone physically touched grass.

### 🚲 Weekend Adventures: walk, ride, touch grass

On Saturday/Sunday during daylight:

- **Walking quest:** Complete the normal timed step goal and submit a new nature photo.
- **Bicycle quest:** Opt in to **precise GPS** tracking; ride **1,500 m** over the required mission time, then photograph the current mission's nature subject. Cycling is optional; choose walking if GPS is unavailable or you do not own a bike.
- **Explore nearby:** Open **Weekend Adventures → Find nearby** to discover cycleway **segments**, parks, gardens and grassy areas through OpenStreetMap Overpass. Previously saved search results are available offline.
- **Open bicycle directions:** Send a selected place to a separate map application. ESCAPE itself does **not** calculate a complete, safe cycling route. Directions can require internet.

**Cycling tracking details:** GPS is used only for an explicitly started bicycle quest. ESCAPE records **total distance**, not the trip route or a history of coordinates. It filters inaccurate fixes and large/implausible jumps; distance remains approximate. A bike ride should always follow local traffic rules. Do not inspect a phone while cycling. Android may stop location tracking when battery restrictions are aggressive.

### 🌙 After dark: bring nature home

Night missions **never require walking outside or cycling in the dark**. Instead, choose a practical offline, paper-based nature challenge:

- Design a five-clue **Nature Bingo** card for tomorrow's walk.
- Write a short **herb-growing plan** using everyday objects.
- Write a five-sentence letter about your favourite outdoor memory.
- Draw up a three-stop **family park adventure** on paper.
- List five things you noticed in nature and describe them in at least 20 words.

**To earn access:** Begin the indoor quest, spend the configured minimum screen-free time, write **at least 20 readable words** including the two on-screen proof words, and take a fresh photo of the paper. Bundled offline ML Kit OCR checks the text; local Gemma can also review **extracted text only**. Merely waiting for a timer **does not** unlock anything.

### 🌅 Sunset-aware, not just a hard-coded 6 PM

If you have searched nearby places and opted in to location, ESCAPE uses the **last locally cached coordinates** and an offline solar-geometry calculation to estimate sunrise and sunset. It switches to indoor quests near twilight (30-minute buffer). If no coordinates are saved, the conservative fallback is **18:00–06:59 in `Europe/Amsterdam`**. 

- Changing the Android timezone does **not** bypass the daypart check.
- Moving the wall clock back during the same boot is resisted using `elapsedRealtime()` and a persisted clock anchor.
- Offline sunset estimates are approximate (not authoritative weather/visibility guidance).
- No offline software can make the time check tamper-proof against a clock adjustment across a reboot.

### 🎧 Pocket-first mode

Tap the **speaker** icon to hear the mission using a **locally installed, non-network Android text-to-speech voice**. Android may need an offline English voice pack installed ahead of time. ESCAPE refuses voices that require internet. During a quest, keep the phone in your pocket; movement is measured by sensors and ESCAPE gives a small vibration when the target is reached. Take photographs only when safely stopped.

### 🔔 Hourly reminders, not hourly AI calls

One new quest reminder per hour while protection is active and a mission is waiting. Gemma generates a cached group of varied missions ahead of time; ESCAPE reuses the cache, so the LLM is **not called for every notification**. A separate gentle weekend prompt may appear once per Saturday/Sunday. Android can delay or suppress background notifications on certain devices.

## What is actually offline?

| Capability | After the one-time setup | Notes |
|---|---|---|
| Local Gemma mission generation | ✅ Offline | LiteRT-LM, Gemma 3 1B text model |
| Photo scene labeling | ✅ Offline | Bundled ML Kit image labeler |
| Handwritten/printed text OCR | ✅ Offline | Bundled ML Kit Latin recognizer; recognition varies |
| Step sensor, photo camera, timer, local notifications | ✅ Offline | Android capabilities |
| GPS bicycle distance | ✅ Offline | Satellite fixes may be slower without data |
| Sunset estimates | ✅ Offline | Requires previously saved coordinates |
| Speak mission | ✅ Offline *if voice installed* | No network-only TTS voices |
| Previous weekend place results | ✅ Offline | Cache on phone |
| Discover **new** cycleways/parks | ❌ Internet | OSM Overpass query; optional |
| External bicycle directions | Depends | Usually a map app + network/offline maps |
| Initial Gemma model download | ❌ Internet | Or one-time local model import |

**Important AI distinction:** `Gemma 3 1B IT` is a **text-only** model. ESCAPE does **not** claim that Gemma can inspect pixels. The bundled ML Kit image labeler checks nature photos; Gemma can optionally reason about the **labels** or **OCR text**. This is not multimodal image understanding by Gemma.

## Tech stack

- **UI:** Flutter / Dart; Android-first application.
- **Android bridge:** Kotlin + Flutter `MethodChannel` (`escape/native`).
- **App monitoring:** `UsageStatsManager` + foreground health service; protected app overlays via `TYPE_APPLICATION_OVERLAY`.
- **Movement:** Android step-counter sensor, clock based on monotonic elapsed time.
- **Weekend ride distance:** opt-in `LocationManager` GPS updates and foreground service `location` type during the ride.
- **On-device AI:** Gemma 3 1B IT `.litertlm` via `com.google.ai.edge.litertlm:litertlm-android:0.17.1` (CPU backend for compatibility).
- **Photo proof:** bundled `com.google.mlkit:image-labeling:17.0.9` and `com.google.mlkit:text-recognition:16.0.1`.
- **Fresh camera image:** Android `ACTION_IMAGE_CAPTURE`, private temporary URI via AndroidX `FileProvider`.
- **Audio:** Android `TextToSpeech` with offline-voice-only policy.
- **Places:** OpenStreetMap / Overpass API, locally cached results, optional external map directions.
- **Storage:** on-device `SharedPreferences`, private Gemma model storage, and a cache of nearby places. No ESCAPE backend/server.

## Get it running on Windows

### Prerequisites

- Flutter SDK installed and on `PATH` (`flutter doctor -v` succeeds).
- Android SDK (platform tools, Android build tools, command-line tools), Java 17, and any NDK version requested by the installed Flutter toolchain.
- Android phone with developer options + USB debugging enabled.
- Android version 7.0 / API 24 or newer for the LiteRT-LM integration; actual AI runtime performance depends on RAM/device.

### Apply the v10 patch over v9

The v10 deliverable is a **changed-files-only** archive. Extract it at the project root while preserving paths. Do not erase existing `android/` or `lib/` files not included in the ZIP.

Example existing directory:

```text
C:\HACKTOBERFEST\escape\
  android\app\build.gradle.kts
  android\app\src\main\AndroidManifest.xml
  android\app\src\main\kotlin\com\example\escape\...
  android\app\src\main\res\xml\escape_file_paths.xml
  lib\...
  pubspec.yaml
  README.md
```

Run:

```powershell
cd C:\HACKTOBERFEST\escape
flutter clean
flutter pub get
flutter run
```

If Flutter or Gradle reports an error, capture the **first compilation error**, not just the final “build failed” line.

### Permissions and why they're needed

| Android permission/access | Reason |
|---|---|
| **Usage Access** (special access) | Detect the current foreground app |
| **Display over other apps** (special access) | Show the social-blocking overlay |
| **Physical Activity** | Count steps |
| **Notifications** (Android 13+) | Show mission reminders/protection notification |
| **Location** (optional, when requested) | GPS-measured cycling or nearby weekend exploration |
| **Foreground service — health/location** | Track an active walk or opt-in bike quest |
| **Internet** | One-time Gemma download and optional nearby-place searches |

No background-location permission is requested. Starting GPS cycling from the visible app is intentional. ESCAPE does not request camera permission when using the external system camera intent with `FileProvider`.

> On OnePlus/OxygenOS, allow ESCAPE background activity and review battery optimization settings. Android can still restrict foreground services/reboot starts. The blocker is a voluntary wellbeing aid, not a hard lock against system-level disable, uninstall or force-stop.

## Install the Gemma model

Expected file:

```text
Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
```

About **584 MB**. The on-device model is **not committed to Git** and is **not embedded in the APK**.

Choose one method:

**A. Import the model downloaded on your phone:** Open ESCAPE → robot/Local AI setup → **Import model from Downloads** → select the `.litertlm`. Android's system picker must be used once to give ESCAPE access to that file. ESCAPE copies it into private app storage.

**B. Automatic first-run download:** Host the model as a properly licensed, public direct-download asset, such as a **GitHub Release asset**, not a Git repository file. Locally add the exact asset URL to `android/local.properties`:

```properties
GEMMA_MODEL_URL=https://github.com/YOUR_USER/escape-digital-detox/releases/download/gemma-v1/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
```

**The example URL is a placeholder**: the release and asset must actually exist. `android/local.properties` is machine-specific; do **not** commit it. Accept and comply with the original model license/redistribution requirements before hosting a public copy.

The Android `DownloadManager` can resume network-paused downloads when supported by the server; terminal failures may need a fresh download. A model already in private storage is reused. You do **not** need to download it again after every launch.

If the model is absent or fails to initialize, ESCAPE falls back to prewritten offline quests—blocking and proof checking remain functional.

## Quick testing

### Demo mode

In Rules, before activating ESCAPE, turn **Demo Mode** on:

- **1 minute** activity goal; **30 walking steps** (if available), or **150 m** opt-in bicycle GPS goal.
- **2 minutes** temporary social access per successful quest.
- **Hourly** mission reminders—not every minute.

Then:

1. Activate ESCAPE and select YouTube/Instagram for protection.
2. Open YouTube: ESCAPE overlay blocks access. Use **Open ESCAPE** or **Go Home** (Android Back may be intercepted by system overlays depending on navigation mode).
3. In ESCAPE, tap **Start Walk Quest**. Keep your phone in your pocket and move for the target time + steps.
4. Return to ESCAPE, capture a *new* photo of the assigned natural subject. The camera unlocks only when the movement goal is met.
5. If ML Kit approves, social access unlocks for two minutes. It becomes locked again after that.
6. On weekend daylight, optionally choose **Start Bicycle Quest (GPS)** instead, after granting **precise** location permission. Move sufficiently far with good GPS reception; then capture the nature clue.
7. After dark, start an indoor paper activity. Wait the minimum time, write 20+ words with the two proof words, and capture the paper using the camera.
8. Speak the mission while offline using the speaker button if your device already has an offline English TTS voice.

**Note:** Demo mode does not generate a full GPS-based route, and image detection has false positives/negatives. An image rejected by the offline labeler should prompt another safe capture—not a risky attempt to get closer to a subject.

### Weekend discovery test

- Online, open **Weekend Adventures → Find nearby** and grant location only when requested.
- Inspect the cycleway segments/parks; open a destination's map directions if desired.
- Turn off internet and reopen Weekend Adventures: the previously saved list should remain available.
- Do not interpret a straight-line distance as route length or a mapped cycleway segment as an end-to-end safe route.

### Package-size check

```powershell
flutter build apk --release --split-per-abi
Get-ChildItem .\build\app\outputs\flutter-apk\*.apk |
  Select-Object Name, @{N='MB';E={[math]::Round($_.Length / 1MB, 1)}}
```

For Google Play, distribute `flutter build appbundle --release` with properly configured production signing instead of the current debug-key release signing placeholder. The native LiteRT runtime and bundled offline photo-labeling/OCR libraries increase APK size; the **584 MB model file is downloaded/imported separately**, not in the package. Measure a real device build before claiming a specific APK size.

## Key files / architecture

```text
lib/
  features/dashboard/dashboard_screen.dart             # main screen
  features/dashboard/widgets/recovery_mission_card.dart # three-clue quest UI
  features/escape/escape_controller.dart                # Dart UI state
  features/weekend/weekend_explorer_screen.dart         # nearby cycleways & parks
  core/native/escape_native_bridge.dart                 # MethodChannel calls

android/app/src/main/kotlin/com/example/escape/
  EscapeMethodChannelHandler.kt   # Android bridge; fresh camera/permission callbacks
  MonitorService.kt               # blocker, one-hour reminders, progress/rewards
  RecoveryProgressTracker.kt      # elapsed time + walking steps
  RideProgressTracker.kt          # opt-in GPS cycling distance, no route stored
  SunsetPlanner.kt                # offline approximate sunrise/sunset
  DaypartClock.kt                 # anchored Amsterdam home clock + daylight policy
  OfflineVoiceGuide.kt            # offline TTS voice filtering
  PhotoProofVerifier.kt          # bundled ML Kit photo labels and OCR
  GemmaMissionGenerator.kt       # mission prompts + curated fallbacks
  MissionPoolManager.kt          # cached batches (AI not run per reminder)
  MissionCoordinator.kt          # model orchestration and extracted-text review
  LockOverlayController.kt       # social-blocking overlay / Go Home
  WeekendExplorer.kt             # optional OSM discovery and saved search results
  GemmaAutoDownloader.kt         # one-time model transfer status/retry
  GemmaModelManager.kt           # local model storage/import

android/app/src/main/AndroidManifest.xml
android/app/src/main/res/xml/escape_file_paths.xml
```

**Flow:** Flutter → native `MethodChannel` → start `MonitorService` → user starts walk/ride/indoor mission → sensors/time accumulate → fresh `ACTION_IMAGE_CAPTURE` photo via `FileProvider` → bundled ML Kit on-device verification → optional Gemma text-only second opinion → native service grants limited access → access expires → next mission.

## Safety, privacy, and limitations

- Photo contents are processed on the phone. Temporary proof photos are deleted after verification; they are **not uploaded** to ESCAPE servers or Gemma endpoints. Do not photograph strangers, addresses, private documents or faces.
- Places searches do send your approximate coordinates to the third-party OSM Overpass endpoint **only when you tap Find nearby**. Do not use that feature if you want to keep your precise location entirely offline.
- Location used for optional GPS riding is not a route recorder and no coordinates/history are persisted by RideProgressTracker. Weekend Explore cache retains the search-area coordinates to support offline daylight and nearby-place features.
- The model license and model-hosting terms apply independently of this project's open-source code license; check those before distributing model weights.
- Android's Usage Access tells ESCAPE the **app package** in the foreground, not the exact website URL in Chrome. Protecting a browser typically protects the *entire* browser.
- Some Android versions and manufacturer ROMs may prevent reboot auto-start, overlays, sensor updates, or notifications. We cannot guarantee background operation on all phones.
- GPS, steps, local sunset and photo classification are imperfect. **Safety comes first**: no road crossings, private property, water-edge risk, cycling while holding the phone, or unsafe night outings should ever be required to unlock a feed.
- Emergency access exists for wellbeing; the user can always disable or uninstall the app. ESCAPE should support choice, not punishment or coercion.
- App compatibility / Google Play approvals for usage access, overlays and foreground services require independent policy review before publishing.

## Development / contribution

This repository is an Android-first Flutter experiment. Pull requests are welcome for safer walking missions, accessibility, better on-device labeling, more precise cycling progress handling, and reproducible tests. Please report Android version, device, Flutter version, and **first error** if you run into a build failure.

### Before committing to GitHub

```powershell
git status
git add android lib README.md pubspec.yaml pubspec.lock .gitignore
git status
git commit -m "feat: turn ESCAPE into offline nature quests"
git push
```

Ensure `.gitignore` excludes `/build/`, `.dart_tool/`, `android/local.properties`, downloaded `*.litertlm`, and APK/AAB output. Publish model weights **as a licensed GitHub Release asset**, never as a regular Git blob.

---

**ESCAPE: Less scrolling. More strolling. 🌿**
