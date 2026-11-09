package com.samibi.stayawake.monitor

/**
 * User-adjustable motion-only detection settings.
 *
 * Main settings: [stillnessSec], [confirmSec].
 * Advanced settings: [thresholdCenti], [windowSec], [sampleHz].
 */
data class MotionSettings(
    /** No movement for this long triggers the gentle warning. */
    val stillnessSec: Int = 45,
    /** Still no movement this long after the warning triggers the alarm. */
    val confirmSec: Int = 10,
    /** Movement threshold in hundredths of m/s^2 (8 = 0.08). */
    val thresholdCenti: Int = 8,
    /** Length of one measurement window. The CPU wakes about once per window. */
    val windowSec: Int = 5,
    /** Accelerometer samples per second. */
    val sampleHz: Int = 5,
) {
    fun toConfig(): MotionConfig = MotionConfig(
        stillnessMs = stillnessSec * 1000L,
        confirmMs = confirmSec * 1000L,
        motionThreshold = thresholdCenti / 100.0,
    )

    val windowMs: Long get() = windowSec * 1000L
    val samplePeriodUs: Int get() = 1_000_000 / sampleHz
    val maxReportLatencyUs: Int get() = windowSec * 1_000_000

    fun coerced(): MotionSettings = MotionSettings(
        stillnessSec = stillnessSec.coerceIn(STILL_MIN, STILL_MAX),
        confirmSec = confirmSec.coerceIn(CONFIRM_MIN, CONFIRM_MAX),
        thresholdCenti = thresholdCenti.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
        windowSec = windowSec.coerceIn(WINDOW_MIN, WINDOW_MAX),
        sampleHz = sampleHz.coerceIn(HZ_MIN, HZ_MAX),
    )

    companion object {
        const val STILL_MIN = 10
        const val STILL_MAX = 300
        const val CONFIRM_MIN = 5
        const val CONFIRM_MAX = 60
        const val THRESHOLD_MIN = 2
        const val THRESHOLD_MAX = 50
        const val WINDOW_MIN = 2
        const val WINDOW_MAX = 30
        const val HZ_MIN = 1
        const val HZ_MAX = 10

        val DEFAULT = MotionSettings()
    }
}
