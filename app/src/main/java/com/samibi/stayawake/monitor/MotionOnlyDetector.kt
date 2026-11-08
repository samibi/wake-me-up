package com.samibi.stayawake.monitor

enum class MotionEvent { NONE, WARN, ALARM }

data class MotionConfig(
    /** No movement for this long triggers the gentle warning. */
    val stillnessMs: Long = 45_000L,
    /** If still no movement this long after the warning, the alarm fires. */
    val confirmMs: Long = 10_000L,
    /** Mean absolute deviation of acceleration magnitude (m/s^2) that counts as movement. */
    val motionThreshold: Double = 0.08,
)

/** Wrist-motion-only detector: stillness -> gentle warning -> alarm unless the wrist moves. */
class MotionOnlyDetector(val config: MotionConfig = MotionConfig()) {

    private var lastMovementMs = 0L
    private var warnedAtMs: Long? = null

    fun start(nowMs: Long) {
        lastMovementMs = nowMs
        warnedAtMs = null
    }

    fun onMotion(nowMs: Long, deviation: Double) {
        if (deviation >= config.motionThreshold) {
            lastMovementMs = nowMs
            warnedAtMs = null
        }
    }

    fun tick(nowMs: Long): MotionEvent {
        val warnedAt = warnedAtMs
        if (warnedAt != null) {
            if (nowMs - warnedAt >= config.confirmMs) {
                lastMovementMs = nowMs
                warnedAtMs = null
                return MotionEvent.ALARM
            }
            return MotionEvent.NONE
        }
        if (nowMs - lastMovementMs >= config.stillnessMs) {
            warnedAtMs = nowMs
            return MotionEvent.WARN
        }
        return MotionEvent.NONE
    }

    fun stillForMs(nowMs: Long): Long = (nowMs - lastMovementMs).coerceAtLeast(0L)
}
