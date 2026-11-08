# Progress

- Added `DetectorParams` (HR drop %, HR-low duration s, stillness duration s); stored in DataStore, replaces sensitivity setting.
- Settings screen: presets (Low/Med/High) + custom -/+ steppers for the three parameters.
- Detector records alarm cause (`HEART_RATE_AND_STILLNESS` / `SYSTEM_ASLEEP`) with values at trigger.
- Home screen: live values color-coded (orange = HR low, blue = still) with progress vs. thresholds.
- Alarm screen: shows cause in color (orange HR, blue stillness, purple watch sleep detection); purple background for system trigger.
- Not built: Gradle can't resolve Android plugin in this sandbox. Verify with `./gradlew :app:testDebugUnitTest`.
