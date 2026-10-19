# AGENTS.md — ImagiPoint working guide

This file is for future coding-agent sessions. Read it before changing the app.

## Project

ImagiPoint is an independent, standalone, offline Wear OS air mouse targeting the Samsung
Galaxy Watch5 Pro. It registers as a Bluetooth HID mouse, uses watch orientation for cursor
movement, and optionally runs a bundled TensorFlow Lite gesture classifier locally.

- Package: `com.imagipoint.watch` (debug suffix `.debug`)
- Visible name: **ImagiPoint**
- minSdk 30, compile/target SDK 35
- Kotlin 2.0.21, AGP 8.7.3, Gradle wrapper 8.11.1, JDK 21
- Compose BOM 2024.12.01; no Wear Compose, Navigation, or Material Icons dependency
- TFLite runtime `org.tensorflow:tensorflow-lite:2.16.1`
- No NDK/CMake. Do not remove native TFLite libraries.
- No `INTERNET` permission. Do not add one without a specific, verified need; the app must
  remain fully usable offline.

## Build and test

```bash
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.12+1-ms   # Codespaces
export ANDROID_HOME=/home/codespace/android-sdk
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Optional on an emulator/device with the app installed (3 instrumentation tests):

```bash
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.imagipoint.watch.debug.test/androidx.test.runner.AndroidJUnitRunner
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` is local-only and gitignored. `ANDROID_HOME` also works. The wrapper
distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`. CI is
`.github/workflows/android.yml`, which has separate build and headless Wear OS emulator
jobs. The emulator job runs the instrumentation suite plus `.github/scripts/emulator-smoke.sh`,
captures screenshots/logcat, and is only as trustworthy as the emulator image: it cannot
validate physical Bluetooth HID, real sensor accuracy, or battery behavior. The root
Playwright files are unrelated pre-existing scaffolding; do not treat them as part of the
Android build.

## Architecture and ownership

```text
MainActivity → ImagipointApp (Compose) → AppGraph
  ├─ SettingsRepository (SharedPreferences, StateFlow)
  ├─ HidManager (BluetoothHidDevice proxy/work thread)
  └─ SessionRuntime (StateFlow<SessionState>)

MouseSessionService (foreground connectedDevice + partial wake lock)
  ├─ MotionSensor → MotionController → CursorMapper → HidManager.move()
  ├─ CalibrationTracker / overlays
  └─ GestureEngine → GesturePreprocessor → TFLite → GestureStabilizer → HidManager
```

- `hid/MouseDescriptor.kt` is pure Kotlin and tested; `hid/HidConstants.kt` contains SDP
  and QoS Android objects. `HidManager` coalesces movement on a HandlerThread, throttles
  reports (~60 Hz), clamps to ±127, and force-releases buttons on stop/failure/disconnect.
- `motion/` uses `TYPE_GAME_ROTATION_VECTOR` (fallback `TYPE_ROTATION_VECTOR`), the world
  projection of device +Y, heading/pitch deltas, 320 ms warmup, outlier rejection, and a
  recenter operation. It deliberately does not integrate acceleration.
- `gestures/` uses a 200×6 window, 200 Hz resampling, 250 ms hop, accel m/s² → g, gyro
  rad/s, channel order accel xyz then gyro xyz, and a reconstructed 30–90 Hz Butterworth.
  Normalization is embedded in the model and must not be repeated.
- `service/` owns session lifecycle and release: stop sensors, stop gestures, release
  buttons, disconnect HID, release wake lock, remove notification, reset state.
- `ui/` is a single-activity Compose app with an original dark design system. Preserve
  round-screen insets, 44 dp primary controls, generous secondary touch targets, and concise text.

## Non-negotiable engineering invariants

1. Never leave a mouse button pressed. Release on every stop/failure/disconnect path.
2. Never report a connected state before the platform confirms the HID connection.
3. Gesture inference must never block or stop cursor movement.
4. Do not register sensors when no session or gesture engine is active. Avoid duplicate
   sensor registrations after resume.
5. Do not create a TFLite interpreter per inference. Reuse it and run inference off the
   UI/pointer path.
6. No unbounded queues, per-sample allocations, or per-sample UI updates.
7. Do not add experimental GPU/NNAPI delegates or cloud/model-download features.
8. Preserve all third-party licenses and attribution. Do not remove upstream notices.
9. Do not root the watch, modify firmware/partitions, or use private APIs/accessibility
   privileges. Keep permissions minimal.
10. Do not commit generated APKs, build directories, keystores, or signing credentials.

## Model contract and provenance

Path: `app/src/main/assets/hgag_gesture_model_float32.tflite`

- SHA-256: `22c8e1141ea279d25d200543f8a7e0a69c2db923eb5023d2a09712af25a76bb2`
- Size: 95,948 bytes
- Input `[1, 200, 6]` float32, output `[1, 11]` float32
- Embedded normalization; do not normalize again
- Labels/metadata: `app/src/main/assets/labels.json`, `model_metadata.json`
- Verified contract enforced by `gestures/ModelContract.kt` and `ModelLoader.kt`
- Actual inference has been verified with Python `ai-edge-litert` and by an Android
  instrumentation test that loads/runs the model through TFLite 2.16.1 on the Wear OS
  emulator. JVM tests verify checksum/metadata but cannot load Android native libraries.
  Gesture accuracy is still unverified.
- 30–90 Hz filter reconstruction is documented as approximate; training filter source was
  not supplied. Do not claim bit-identical preprocessing.
- Gestures are experimental, default off; only Index Thumb Tap → Left click is mapped by
  default. Never guess unverified labels/actions.

## Validation checklist

Before declaring a change complete:

- `:app:testDebugUnitTest` — currently **82 tests**, all passing.
- `:app:assembleDebugAndroidTest` + instrumentation — **3 tests**, passing on the Wear OS 5
  emulator (TFLite runtime, Compose navigation, settings persistence). CI's emulator job
  runs these plus the ADB screenshot/logcat smoke script.
- `:app:lintDebug` — currently **0 errors**; warnings are dependency/version and adaptive
  icon folder suggestions.
- `:app:assembleDebug` succeeds.
- Recheck APK manifest for no INTERNET/location/accessibility/admin permissions and correct
  standalone watch metadata.
- Recheck model asset SHA and size if assets changed.
- Exercise repeated start/stop, disconnect during active session, calibration failure,
  model-missing/malformed failure, and button release paths.
- A Wear OS 5 emulator (454 x 454 px / 227 dp round) was used for UI, navigation, scroll
  inset, gesture-mapping, diagnostics, idle-calibration, and screenshot checks. It cannot
  validate Bluetooth HID host pairing/reports, real sensor rates, battery, cursor feel,
  gesture accuracy, or hardware round-screen appearance.
- The GitHub Actions pipelines were authored and validated locally (YAML parse, SDK image
  and AVD profile discovery, APK entry-point extraction, and the exact emulator smoke
  script). They have not run on GitHub because no push was performed in this checkout.
- Hardware Bluetooth HID, real sensor rates, battery, cursor feel, gesture accuracy, and
  actual-watch rendering were **not tested in Codespaces**. Never claim they were. Report
  this limitation in README and final reports until tested on a real Galaxy Watch5 Pro and
  host.

## Attribution obligations

Keep these present in `README.md`, `THIRD_PARTY_NOTICES.md`, the APK notices asset, and the
in-app About/Licenses screens:

- **HGAG-DATA** (CC BY 4.0), authors Khalid A. Abbas, Mofeed Turky Rashid, Luigi Fortuna,
  DOI `10.17632/mkhn7kxjvy.3`, publication DOI `10.1016/j.dib.2025.111558`. Do not conflate
  dataset authorship with model authorship/licensing.
- **WearMouse** (Apache 2.0, Copyright 2018 Google LLC) for the adapted HID descriptor and
  general lifecycle approach. Keep the Apache header in adapted source.
- **Doublepoint Touch SDK** as algorithmic inspiration only. The inspected repo declares
  ISC in `package.json` but had no LICENSE/COPYING file; no code was copied and no runtime
  dependency exists. Do not claim otherwise.
- **TensorFlow Lite 2.16.1** (Apache 2.0).
- **WowMouse APK** is behavioral reference only; no code/assets/binary redistributed.
- Original ImagiPoint code is Apache 2.0; keep `LICENSE` and `licenses/Apache-2.0.txt`.
- `app/src/main/assets/THIRD_PARTY_NOTICES.txt` must stay in sync with
  `THIRD_PARTY_NOTICES.md`.

## Current known limitations

- No physical-watch or real-host HID test has been performed here.
- Left-handed mode flips horizontal only; 180° wrist orientation may need vertical
  inversion, unverified.
- Gesture domain gap from HGAG research sensor to watch; accuracy unknown.
- Debug APK is ~37 MB and unsigned for release; release signing is intentionally not set up.
- Host pairing is host-initiated; app lists bonded devices and does not scan.
