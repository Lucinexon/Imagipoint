# ImagiPoint

**A standalone, fully offline Wear OS air mouse for the Galaxy Watch5 Pro.**

ImagiPoint turns wrist and forearm movement into precise Bluetooth mouse input. It
registers the watch as a standard Bluetooth HID mouse, so it works with computers that
accept a Bluetooth mouse, without a companion phone app, account, cloud service, or
internet connection.

The interface is designed specifically for a round OLED watch display: true black,
precision-instrument surfaces, a single solar amber accent, an instrument dial for the
primary action, compact typography, and 44 dp controls for a 227 dp screen.

> **Status — engineering build.** The project compiles, 82 JVM tests and three on-device
> instrumentation tests pass, Android Lint passes with no errors, and a debug APK has been
> produced and statically inspected.
> Bluetooth pairing, HID input, sensor rates, battery drain, and gesture accuracy have
> **not** been validated on a physical Galaxy Watch5 Pro or a real host in this
> development environment. Gestures are therefore experimental and disabled by default.
> See [Verification](#verification) and [Limitations](#limitations).

---

## Features

- **Bluetooth HID mouse** using the platform `BluetoothHidDevice` profile. No root,
  firmware modification, accessibility service, or private API is used.
- **Orientation-based cursor control** from the watch's game rotation vector. The
  controller uses orientation deltas, not double integration of accelerometer data.
- **Safe calibration** with a visible "hold still" step and a 900 ms stability window.
  The reference is never silently reset while the pointer is moving.
- **Adjustable sensitivity, smoothing, dead zone, and handedness.**
- **Bounded HID reports**: signed 8-bit relative X/Y/wheel, ±127 per report, sub-pixel
  accumulation, overflow deliberately discarded, coalesced reports at about 60 Hz.
- **Stuck-button protection**: buttons are released on stop, failure, disconnect, sensor
  loss, calibration, and teardown. Clicks always include a release.
- **Foreground session service** with a Stop action in the notification, a partial wake
  lock only while the session is active, and full sensor/HID cleanup on stop.
- **Allocation-conscious hot paths**: reusable HID report/gesture buffers, an
  allocation-free calibration ring, and 1 Hz diagnostics publishing so the pointer's
  sensor path is never blocked by UI or telemetry work.
- **Optional on-device gesture recognition** using the supplied TensorFlow Lite model.
  Inference runs on its own background thread and never blocks cursor movement.
- **Verified, local model loading**: SHA-256, metadata, labels, and tensor shapes are
  checked before inference. Failure disables gestures only.
- **No internet permission. No telemetry, ads, analytics, accounts, cloud inference, or
  runtime downloads.**
- **Real connection states only**: availability, permission, pairing/discoverability,
  connecting, connected, failure, and disconnection states come from the platform.
- **In-app Credits and Licenses** with the complete third-party notices.

---

## Requirements

- Wear OS 3.0 or newer (minSdk 30). Target device: **Samsung Galaxy Watch5 Pro**.
- A watch with Bluetooth Classic HID support, accelerometer, and gyroscope.
- A host computer that supports Bluetooth HID mice (Windows, macOS, Linux, or another
  compatible Bluetooth host).
- Android SDK 35 and JDK 21 to build. No NDK or CMake is required.

ImagiPoint is a watch-only app (`android.hardware.type.watch` required) and declares
`com.google.android.wearable.standalone=true`. It does not require a phone companion.

---

## Install and pair

1. Install the debug APK on the watch:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Enable ADB debugging on the watch first (Developer options). The debug package is
   `com.imagipoint.watch.debug`; the visible app name is **ImagiPoint**.
2. Open ImagiPoint and allow **Nearby devices** access. The app asks for
   `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE`, plus notification permission on
   Android 13+. Bluetooth is required; notification access is not.
3. Open **Connection** and tap **Make watch discoverable**.
4. On the computer, add a Bluetooth device and select **ImagiPoint**. Confirm pairing on
   both devices if prompted.
5. Select the paired computer on the watch. The Connection screen reports the real HID
   connection state.
6. Return home and tap **Start**. Hold your wrist steady during the short calibration
   step. Move your forearm to move the pointer.

Pairing is host-initiated. ImagiPoint does not perform Bluetooth scanning and does not
request location permission. If your host requires a pairing code, accept the request on
both devices.

### Using the mouse

- **Start / Stop** is the large control on the home screen. Stop is always available
  while a session is running.
- **Recenter** from the calibration screen or the target button on the home screen.
  Recentering pauses pointer output and requires a stable posture.
- **Connection** shows paired hosts, the active connection, and a disconnect action.
- **Settings** contains pointer tuning, gesture options, diagnostics, and credits.
- Gesture actions, when enabled, use confidence, two-window stability, and cooldown
  gating. The only default mapping is **Index Thumb Tap → Left click**.
- A second tap on Start while a session is already running does not start a duplicate
  session.

If the app is stopped, the service is removed, sensors are unregistered, held buttons are
released, and the HID connection is closed. Notification Stop performs the same cleanup.

---

## Design

ImagiPoint's visual system is an original, watch-first "precision instrument" language
built directly on Compose:

| Token | Value |
|---|---|
| Background | true black `#000000` (OLED pixels stay off) |
| Surfaces | obsidian `#050506`, graphite `#0A0B0C`, panel `#101113`, raised `#191A1D` |
| Hairlines | `#282A2D` / `#1C1E20` |
| Primary text | warm off-white `#F4F2EC` |
| Secondary text | titanium `#A7A6A0` / `#6B6D6C` |
| Accent | solar amber `#FFB45C` |
| Success / warning / danger | `#8CD7AD` / `#E8C47B` / `#FF7D73` |

Type is a compact scale (display 34/28 sp, headline 22/19 sp, title 16/13 sp, body
13/12/10 sp) with tightened tracking, plus a monospaced tabular style for live
diagnostics. The home screen centres on a 104 dp instrument dial: a static 48-tick
bezel, a restrained state/progress arc, and a radial-gradient core carrying the current
action. Panels use a subtle vertical gradient and an optional accent hairline instead of
stacked bordered cards. Primary controls are at least 44 dp and secondary targets are
44–46 dp. Screen content uses 22 dp horizontal insets and a 64–72 dp bottom inset so
text stays inside the 454 px (227 dp) round safe area; the header keeps a 44 dp
back/action row and a consistent title baseline.

Motion is limited to 170 ms screen fades, short arc transitions, and confirmation
haptics. There is no continuously animating decoration, status dots do not pulse, and
the UI never redraws at sensor or inference rate. Icons remain original Canvas drawings
on a 24×24 grid; no Material icon pack, Wear Compose, Navigation, or Apple assets/fonts
are used.

The design is inspired by restraint, hierarchy, and precision associated with modern
platform design. It is not affiliated with, endorsed by, or branded as an Apple product.

---

## Architecture

```text
MainActivity / ImagipointApp (Compose UI, permissions, screen state)
        │
        ├── SettingsRepository (SharedPreferences + StateFlow)
        ├── HidManager (BluetoothHidDevice proxy, registration, reports)
        └── SessionRuntime (StateFlow<SessionState>)

MouseSessionService (foreground, connectedDevice, partial wake lock)
        ├── HidManager ── Bluetooth HID mouse reports ── host computer
        ├── MotionSensor (rotation vector, dedicated HandlerThread)
        │      └── MotionController → CursorMapper → HidManager.move()
        ├── CalibrationTracker (stability + drift)
        └── GestureEngine (optional, independent HandlerThread)
               ├── GesturePreprocessor (200×6 window, 200 Hz, bandpass)
               ├── TensorFlow Lite interpreter (reused, 1 thread)
               └── GestureStabilizer → mapped HID click/scroll
```

Key invariants:

- Cursor tracking and gesture inference are independent. A gesture model failure or slow
  inference cannot stop pointer movement.
- HID reports are produced on a dedicated worker. Sensor callbacks do not block on a
  Binder call to the Bluetooth stack.
- Button releases are force-sent and are not dropped by movement throttling.
- No sensor listeners are registered while the app is idle.
- The gesture interpreter is created once per gesture-engine lifetime, never per
  inference.
- UI state is published at about 4 Hz, not once per sensor sample.
- No unbounded queues: movement is coalesced, gesture windows are built only when one
  second of data exists, and inference is skipped if the previous run is still busy.

### Cursor mapping

The orientation controller uses the world-space projection of the watch's local **+Y**
axis (the 12 o'clock / forearm-pointing direction):

- heading = `atan2(p.x, p.y)`
- pitch = `asin(p.z)`
- `dx = +Δheading × 900 px/rad × sensitivity`
- `dy = −Δpitch × 900 px/rad × sensitivity`

`+Y` was chosen because it follows the forearm rather than the screen normal; rolling the
wrist around the forearm does not change the pointing direction. Smoothing is exponential
on angular rate, the dead zone is radial in px/s, and the mapper keeps sub-pixel
remainders. Left-handed mode flips horizontal movement. See
[Limitations](#limitations) for the unverified 180° wrist-orientation case.

The Touch SDK's `dispatchRayCasting()` calculation was reviewed as an algorithmic
reference. ImagiPoint does not copy or run that JavaScript SDK; the shipped controller
uses quaternion orientation deltas instead. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## Build from the command line

Prerequisites:

- JDK **21** (the Gradle wrapper is 8.11.1)
- Android SDK platform **35** and build-tools **35.0.0**
- `local.properties` pointing at the SDK, or `ANDROID_HOME` set

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Optional instrumentation suite (model runtime plus real Compose UI smoke tests; requires
a watch or emulator with the app installed):

```bash
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.imagipoint.watch.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper distribution is pinned and checksum-verified:

```text
Gradle 8.11.1
SHA-256 f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
```

Toolchain pins are in `app/build.gradle.kts` and `build.gradle.kts`: AGP 8.7.3,
Kotlin 2.0.21, Compose BOM 2024.12.01, TensorFlow Lite 2.16.1, compile/target SDK 35,
minSdk 30. No NDK/CMake build is present or required.

### Continuous integration

`.github/workflows/android.yml` installs JDK 21 and Android SDK 35, validates the wrapper,
runs unit tests and lint, builds the debug and release APKs, and uploads each APK only
after a successful build. A dedicated step measures both APKs with `stat -c %s` and fails
the job if either reaches the hard 100,000,000-byte limit; exact sizes are written to the
workflow step summary. The release APK is also checked with `aapt2 dump permissions` for
an accidental `INTERNET` permission. Failure reports and test results are uploaded as
diagnostics. Generated APKs and build directories are not committed.

The repository also contains unrelated, pre-existing Playwright scaffolding. It is not
part of ImagiPoint and is not required to build or use the app.

---

## Gesture model and preprocessing

The bundled model is preserved byte-for-byte at:

```text
app/src/main/assets/hgag_gesture_model_float32.tflite
```

Verified directly with Python `ai-edge-litert` and flatbuffer inspection:

| Property | Verified value |
|---|---|
| SHA-256 | `22c8e1141ea279d25d200543f8a7e0a69c2db923eb5023d2a09712af25a76bb2` |
| Size | 95,948 bytes |
| Input | `[1, 200, 6]`, float32 |
| Output | `[1, 11]`, float32 |
| Normalization | embedded in the model (mean subtraction, 1/std multiplication) |
| Dataset rate | 200 Hz |
| Window | 200 samples (1,000 ms), 50-sample (250 ms) hop |
| Channel order | `accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z` |

The 11 labels, in output order, are:

`Clapping, Coin Flipping, Finger Snapping, Fist Making, Horizontal Wrist Extension,
Index Finger Flick, Index Thumb Tap, Shooting, Thumb Up, Wrist Extension, Wrist Flexion`.

Runtime preprocessing:

- Accelerometer is converted from Android m/s² to **g** (divide by 9.80665); gyroscope is
  used directly in **rad/s**. These units are inferred from the embedded normalization
  statistics and are documented as an inference, not as recovered training source.
- Accel and gyro samples are time-stamped, buffered, and linearly resampled to 200 points
  over the latest 995 ms.
- A fourth-order Butterworth bandpass at 30–90 Hz is applied, reflecting the HGAG-DATA
  paper's published preprocessing. The original training filter implementation is not
  present in the supplied artifacts; ImagiPoint uses a deterministic zero-phase
  forward/backward implementation with reflection padding. This is an honest
  reconstruction, not a claim of bit-identical preprocessing.
- Normalization is **not** repeated in app code because it is embedded in the model.
- Inference runs every 250 ms on a background thread with one TFLite thread. Output must
  be finite, in [0,1], and sum to approximately 1; otherwise the prediction is discarded.

**Domain-gap warning.** HGAG-DATA was recorded at 200 Hz with a research sensor; the
dataset describes mechanomyographic signals in the 30–90 Hz band. A Galaxy Watch5 Pro may
provide accelerometer/gyro samples at a lower rate, and resampling cannot recreate missing
high-frequency information. The supplied metadata itself warns about this. Gesture
accuracy on the watch is **unverified** until tested with labelled watch recordings.
Gestures are therefore experimental and off by default. The pointer works without them.

The model's training provenance, label order, and normalization are from the supplied
metadata. No model training, online learning, recording, cloud inference, or runtime model
download is implemented. No separate model license is asserted; the dataset attribution
below does not imply authorship or licensing of the model weights.

---

## Verification

Commands run in this checkout:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
adb shell am instrument -w \
  com.imagipoint.watch.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Results:

- **82 JVM unit tests pass.**
- **Three instrumentation tests pass on the Wear OS 5 emulator.** They cover the pinned
  Android TFLite runtime loading/running the model (11 finite probabilities summing to
  1.0), Compose navigation through Home, Connection, Settings, and Calibration, and
  persistence of the Diagnostics toggle to SharedPreferences. Re-verified after the redesign
  and runtime optimization: `OK (3 tests)`, and the full local smoke script passed with
  five 454 x 454 screenshots (`/tmp/opencode/smoke/screenshots/01-home.png` …
  `05-connection.png`).
- **Android Lint: 0 errors**, 10 warnings (pinned-dependency update suggestions and an
  adaptive icon folder warning; warnings do not fail the build).
- **Hot-path allocations removed:** the HID send path reuses one 4-byte report array and
  primitive locals (no per-report snapshot object), gesture inference reuses its input/output
  `FloatBuffer` views and 11-float output array, the calibration tracker is a fixed ring
  buffer with no per-sample allocation, and diagnostics publish at 1 Hz instead of 4 Hz.
  Cursor motion, button-release, and model preprocessing semantics are unchanged.
- **Debug APK built successfully:** `app/build/outputs/apk/debug/app-debug.apk`
  (38,393,234 bytes; SHA-256
  `5b5bbaf1ae23490ef0f7be8a44b57577980b982801aa67a28cb6d132ae589456`).
- **Release APK built successfully:** `app/build/outputs/apk/release/app-release-unsigned.apk`
  (16,645,306 bytes; SHA-256
  `9540c7fda303d29fd2bca0861f8674af7aca54ef87c00d1881d6f14f8b09ce53`), with R8 minification
  and resource shrinking enabled. It stays unsigned by design; both APK sizes are far below
  the 100,000,000-byte hard limit enforced in CI.
- **Emulator-only idle resource sample** (debug build, Home screen, no active HID session —
  Bluetooth HID cannot run in the emulator): TOTAL PSS ≈ 63.3 MB (RSS ≈ 161.8 MB) and process
  CPU 0% when idle. This is not a Galaxy Watch5 Pro measurement and says nothing about
  battery drain or active-session CPU.
- APK inspected with `aapt2`: label **ImagiPoint**, minSdk 30, target 35, standalone watch
  app, foreground `connectedDevice` service. Permissions are limited to Bluetooth
  connect/advertise (legacy Bluetooth for API 30), foreground service, notifications, and
  wake lock. **No INTERNET, location, accessibility, admin, telemetry, or ad
  permissions.**
- APK contains the model, labels, metadata, notices asset, and TFLite native libraries.
- Python `ai-edge-litert` loaded the model, confirmed input `[1,200,6]` and output
  `[1,11]`, and executed an inference whose 11 outputs summed to 1.0. The Android
  instrumentation test independently repeats this through the app's actual runtime.
- **Wear OS emulator UI validation:** a Wear OS 5 emulator at 454 x 454 px (227 dp, round)
  installed and launched the debug APK after the redesign. The gradient panels, 104 dp
  instrument dial, monospaced diagnostics, and round-safe insets were checked screen by
  screen (Home, Connection, Settings, Calibration, Gesture Mappings, Diagnostics, About,
  Licenses, and the permission gate), with layout fixes applied to the Connection empty
  state and the Calibration subtitle. Permission onboarding, Home, Connection,
  Settings (including the custom slider and end-of-list inset), gesture mappings and the
  action picker, Diagnostics model integrity, idle Calibration, About, Licenses, and
  hierarchical Back navigation were exercised. The calibration progress/failure overlays
  were layout-reviewed only, because they require a live HID connection. Emulator
  Bluetooth cannot validate host pairing or HID reports.
- **Automated emulator CI:** `.github/workflows/android.yml` has separate build and
  emulator jobs. The emulator job installs the API 34 Wear OS 5 x86_64 image, verifies the
  `wearos_large_round` profile and KVM acceleration, boots headless, runs all
  instrumentation tests plus the ADB UI smoke script, and uploads screenshots,
  instrumentation output, and logcat on every run. The workflow was not executed on
  GitHub from this checkout because nothing was pushed. The same smoke script was run
  locally against an API 34 Wear OS emulator and passed.

Tests cover cursor axes and handedness, calibration, sensitivity, dead zone, smoothing,
sensor-buffer boundaries and invalid data, HID report limits and button release,
descriptor structure, repeated settings decode/encode, gesture confidence/stability/
cooldown, model checksum and metadata contract, and resampling/preprocessing boundaries.
The instrumentation tests additionally check the Android TFLite runtime path, core screen
navigation, and settings persistence. CI's emulator job runs those tests and captures
screenshots plus logcat; it still cannot test physical Bluetooth HID, sensor accuracy, or
battery behavior.

### Not verified here

- Physical Galaxy Watch5 Pro installation, Bluetooth pairing, and HID operation on a real
  host.
- Real sensor sample rates, latency, CPU behavior, memory, and battery drain.
- Cursor feel, gesture accuracy, and round-screen rendering on the actual watch.
- Calibration overlay rendering with a live HID session (layout-reviewed in code only).

Emulator or unit-test success does not prove hardware Bluetooth compatibility. These
checks remain required before calling the product hardware-validated.

---

## Privacy and offline behavior

- The runtime uses only local sensors, a bundled local model, local preferences, and
  Bluetooth HID to the paired host.
- The manifest has **no `INTERNET` permission**.
- There are no ads, analytics, crash uploads, accounts, remote configuration, CDNs,
  WebViews, or automatic data uploads.
- The model and all notices are bundled in the APK.
- Diagnostics are held in memory and are not logged continuously or recorded.
- Settings are stored locally in `SharedPreferences`; Android backup is disabled.
- Development-time Gradle dependency downloads are build-time activity, not runtime
  behavior.

After installation, normal operation requires no network access. This has been verified
by manifest inspection and code review, not by installing the app on a network-isolated
watch.

---

## Limitations

- **Hardware validation is outstanding.** Bluetooth HID, sensor fusion, and gesture
  behavior must be tested on the Galaxy Watch5 Pro and on each target host OS.
- **Gestures are experimental.** The model was trained on a different sensor domain and
  may not generalize. They are disabled by default; model/preprocessing failures never
  affect the mouse.
- The 30–90 Hz bandpass is reconstructed from the dataset description. The original
  training filter implementation, exact `filtfilt`/causal choice, and edge handling are
  not available in the supplied artifacts.
- Accelerometer units (g) and gyroscope units (rad/s) are inferred from normalization
  statistics. They still need confirmation with labelled watch data.
- Handedness currently flips horizontal movement only. A watch worn rotated 180° may need
  vertical inversion; this is not implemented as a separate setting because it has not
  been validated.
- The app lists bonded hosts and relies on host-initiated pairing; it does not scan for
  computers or implement its own pairing UI.
- Bluetooth HID support varies by host and OS. Some hosts may require removing and
  re-pairing the watch after an app update.
- The debug APK is about 37 MB and includes debug symbols and multiple CPU ABIs. A release
  build is not signed or produced here.
- The foreground service and partial wake lock are intentionally limited to an active
  session. An active session is meant to be user-initiated and stoppable from the watch or
  its notification.
- The historical WowMouse APK was used only for static behavioral reference. ImagiPoint is
  an independent product and is not an official successor or affiliated product.

---

## Attribution

Full notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), bundled in the APK,
and shown in **Settings → About & credits → Licenses & notices**.

### HGAG-DATA

Gesture-recognition model trained using the Hand Gesture Accelerometer and Gyroscope
Dataset (HGAG-DATA), Version 3, by Khalid A. Abbas, Mofeed Turky Rashid, and Luigi
Fortuna. Mendeley Data (2025). <https://doi.org/10.17632/mkhn7kxjvy.3>. Licensed under
Creative Commons Attribution 4.0 International (CC BY 4.0):
<https://creativecommons.org/licenses/by/4.0/>.

Abbas, K. A., Rashid, M. T., & Fortuna, L. (2025). "The mechanomyographic dataset of hand
gestures harvested using an accelerometer and gyroscope." *Data in Brief*, 60, 111558.
<https://doi.org/10.1016/j.dib.2025.111558>

The dataset citation is distinct from the authorship, licensing, and provenance of the
supplied model weights.

### WearMouse

<https://github.com/ginkage/wearmouse> — Apache License 2.0, Copyright 2018 Google LLC.
ImagiPoint adapts the Bluetooth HID mouse report descriptor and general report lifecycle
approach. The adapted descriptor/constants retain an Apache 2.0 attribution header. The
Kotlin HID manager, session service, motion controller, UI, and design system are original
ImagiPoint work.

### Doublepoint Touch SDK reference

<https://github.com/basherbots/touch-sdk-js> — the `dispatchRayCasting()` calculation was
reviewed for algorithmic inspiration. The inspected repository declares ISC in
`package.json`, but no LICENSE or COPYING file was present at the inspected commit. No
source code was copied or shipped, and there is no JavaScript/runtime dependency. The
shipped controller is independently written in Kotlin using quaternion orientation
deltas.

### TensorFlow Lite

TensorFlow Lite 2.16.1, Copyright Google LLC, Apache License 2.0. Used only for local,
offline inference of the bundled model.

### Historical WowMouse APK

Used only as a static behavioral/UX reference. No proprietary implementation, assets, or
APK binary is bundled or redistributed. ImagiPoint is not affiliated with WowMouse.

---

## License

Original ImagiPoint source code and documentation are provided under the Apache License,
Version 2.0. See [LICENSE](LICENSE) and [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).
Retained upstream work remains subject to its original notices and licenses. The HGAG-DATA
dataset attribution and CC BY 4.0 notice are provided separately above.

ImagiPoint is not affiliated with Apple Inc., Google LLC, Samsung Electronics, Doublepoint,
WearMouse, or WowMouse. Product and company names are used only for identification and
attribution. Apple-inspired design language refers to general principles of restraint and
clarity; no Apple logo, assets, fonts, or branding are used.

---

## Repository layout

```text
app/src/main/java/com/imagipoint/watch/
  hid/          Bluetooth HID descriptor, reports, manager
  motion/       sensor input, quaternion math, cursor mapping, calibration
  gestures/     model loader/contract, preprocessing, TFLite engine, stabilizer
  service/      foreground mouse session service
  session/      shared session state and metrics
  settings/     persisted settings codec/repository
  ui/           theme, components, icons, screens, root app
app/src/main/assets/   model, labels, metadata, bundled notices
app/src/test/          82 JVM unit tests
app/src/androidTest/   3 tests: TFLite runtime, navigation, settings persistence
.github/scripts/       emulator instrumentation/UI smoke runner
.github/workflows/     Android CI: build + Wear OS emulator smoke
```
