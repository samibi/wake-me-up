# Progress

- Added `DetectorParams` (HR drop %, HR-low duration s, stillness duration s); stored in DataStore, replaces sensitivity setting.
- Settings screen: presets (Low/Med/High) + custom -/+ steppers for the three parameters.
- Detector records alarm cause (`HEART_RATE_AND_STILLNESS` / `SYSTEM_ASLEEP`) with values at trigger.
- Home screen: live values color-coded (orange = HR low, blue = still) with progress vs. thresholds.
- Alarm screen: shows cause in color (orange HR, blue stillness, purple watch sleep detection); purple background for system trigger.
- Not built: Gradle can't resolve Android plugin in this sandbox. Verify with `./gradlew :app:testDebugUnitTest`.

## Branch motion-only (2026-11-08)
- Replaced HR+stillness detection with wrist-motion-only: 45s still -> gentle double buzz -> 10s more still -> alarm. Movement cancels.
- Accelerometer 5 Hz, batched to ~5 s; no exercise session, no HR, no passive client. Diag motion log every ~30 s.
- Fixed merge bug: `sensitivity` unresolved in MonitoringService (broke build after merging both branches).
- VERIFIED: `./gradlew :app:testDebugUnitTest` 16 tests pass (8 new MotionOnlyDetectorTest); `assembleDebug` OK (JAVA_HOME must be java-17, not Android Studio jbr).
- NOT VERIFIED: on-watch behavior, CPU/battery gain, motion threshold 0.08 tuned for a seated wrist. Home/Settings screens still show HR fields (now unused).
- Tests were not seen failing first for the right reason (first run died on the unrelated compile error).

## Branch motion-only: settings screens (2026-11-09)
- Settings: "Warn after no movement" (10-300s, step 5) and "Alarm after warning" (5-60s, step 5). Old HR/preset controls removed (they did nothing on this branch).
- New "Advanced settings" screen: movement threshold (0.02-0.50, step 0.01), measurement window (2-30s), samples per second (1-10), reset to defaults.
- Values stored in DataStore (`MotionSettings`), read once at session start; changes apply on next session. Home "Still: x/Ns" now shows the real target.
- VERIFIED: RED first (MotionSettingsTest failed: unresolved reference), then GREEN; full `testDebugUnitTest --rerun-tasks`: 22 tests, 0 failures (8 old detector, 8 new detector, 6 MotionSettings); `assembleDebug` OK.
- NOT VERIFIED: UI on a watch (layout, stepper feel); DataStore persistence across restarts (no instrumented test); sensor behavior at non-default window/Hz.
- Home screen still shows the HR lines (unused). Not touched.
- Added one-line hint under each setting (5 settings). VERIFIED: tests pass, APK builds, hint text found in dex. NOT VERIFIED: how the text fits the watch screen.
