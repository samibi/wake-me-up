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
