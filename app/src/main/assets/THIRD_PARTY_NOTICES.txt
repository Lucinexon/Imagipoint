# Third-Party Notices

ImagiPoint is an independent Wear OS application. This file records the provenance,
licenses, and attribution for third-party work that informed or is bundled with the
application. It is also bundled inside the APK as
`assets/THIRD_PARTY_NOTICES.txt` and shown in the in-app Licenses screen.

## ImagiPoint

Original application code, visual design, and documentation:
Copyright 2026 ImagiPoint contributors.

Licensed under the Apache License, Version 2.0. A copy is provided in
`licenses/Apache-2.0.txt`. ImagiPoint is not affiliated with or endorsed by any of the
projects or companies listed below.

## WearMouse

Upstream repository: https://github.com/ginkage/wearmouse

Copyright 2018 Google LLC.

Licensed under the Apache License, Version 2.0.

ImagiPoint adapted the Bluetooth HID mouse report descriptor and the general
report-sizing/lifecycle approach from WearMouse. The adapted descriptor and constants
retain an Apache 2.0 header identifying WearMouse and Google LLC. The surrounding
Kotlin HID manager, session lifecycle, motion controller, and UI are independent
ImagiPoint work. The full Apache License 2.0 text is included in
`licenses/Apache-2.0.txt`.

## Doublepoint Touch SDK reference

Reference repository: https://github.com/basherbots/touch-sdk-js

ImagiPoint reviewed the `dispatchRayCasting()` approach of deriving arm-pointing
changes from gyroscope angular velocity and a normalized gravity vector. The shipped
motion controller is an independently written Kotlin implementation based on
quaternion orientation deltas and is not a copy of the JavaScript SDK. No JavaScript,
WebView, CDN import, WatchBridge, or runtime dependency on the Touch SDK is included.

The inspected repository declares ISC in `package.json`, but no LICENSE or COPYING
file was present at the inspected commit. Consequently, no Touch SDK source code was
copied into ImagiPoint. This notice records the algorithmic inspiration only.

## HGAG-DATA gesture dataset

Gesture-recognition model trained using the Hand Gesture Accelerometer and Gyroscope
Dataset (HGAG-DATA), Version 3, by Khalid A. Abbas, Mofeed Turky Rashid, and Luigi
Fortuna. Mendeley Data (2025). https://doi.org/10.17632/mkhn7kxjvy.3. Licensed under
Creative Commons Attribution 4.0 International (CC BY 4.0):
https://creativecommons.org/licenses/by/4.0/.

Associated publication:

Abbas, K. A., Rashid, M. T., & Fortuna, L. (2025). "The mechanomyographic dataset of
hand gestures harvested using an accelerometer and gyroscope." *Data in Brief*, 60,
111558. https://doi.org/10.1016/j.dib.2025.111558

The dataset citation describes the training data. It is distinct from authorship,
licensing, or provenance of the supplied model weights. The model file
`hgag_gesture_model_float32.tflite` was supplied with this project; this notice does
not assert a separate model license or claim that CC BY 4.0 automatically resolves
every question about derived weights.

## TensorFlow Lite runtime

TensorFlow Lite 2.16.1 (`org.tensorflow:tensorflow-lite:2.16.1`), Copyright Google LLC.
Licensed under the Apache License, Version 2.0. Used only for local, offline inference
of the bundled gesture model. The runtime is loaded on-device; no model download,
cloud inference, or network access is used.

## AndroidX, Jetpack Compose, Kotlin, and Kotlin Coroutines

This application uses AndroidX Core, AndroidX Activity, AndroidX Lifecycle, Jetpack
Compose (UI, Foundation, Animation, Material 3), Kotlin, and Kotlin Coroutines.
These components are licensed under the Apache License, Version 2.0, by their
respective copyright holders (Google LLC and JetBrains s.r.o.). They are consumed as
standard Gradle dependencies; their license texts are available from their upstream
distributions.

## Historical WowMouse APK

The separately supplied WowMouse APK was inspected statically as a behavioral and
user-experience reference only. It is not bundled, redistributed, decompiled into
ImagiPoint, or represented as a predecessor or official product. ImagiPoint contains
no WowMouse assets or implementation code.

## CC BY 4.0 notice

The HGAG-DATA material is licensed under CC BY 4.0. To view a copy of this license,
visit https://creativecommons.org/licenses/by/4.0/. Attribution is provided in the
README, this file, and the in-app About/Credits screen.

## Apache License 2.0 notice

A copy of the Apache License 2.0 is provided at `licenses/Apache-2.0.txt` in the
source distribution and is included in the APK's notice text. Where Apache-licensed
source was adapted, the copyright and license header is retained in the source file.
