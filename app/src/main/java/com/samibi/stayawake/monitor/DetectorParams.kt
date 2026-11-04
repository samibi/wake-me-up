package com.samibi.stayawake.monitor

import kotlin.math.roundToInt

/** User-adjustable detection thresholds. */
data class DetectorParams(
    val hrDropPercent: Int,
    val hrLowSustainSec: Int,
    val stillnessSec: Int,
) {
    fun toConfig(): DetectorConfig = DetectorConfig(
        hrDropFraction = hrDropPercent / 100.0,
        hrLowSustainMs = hrLowSustainSec * 1000L,
        stillnessMs = stillnessSec * 1000L,
    )

    companion object {
        const val DROP_MIN = 2
        const val DROP_MAX = 25
        const val SUSTAIN_MIN = 5
        const val SUSTAIN_MAX = 300
        const val STILL_MIN = 5
        const val STILL_MAX = 600

        fun forSensitivity(s: Sensitivity): DetectorParams {
            val c = DetectorConfig.forSensitivity(s)
            return DetectorParams(
                hrDropPercent = (c.hrDropFraction * 100).roundToInt(),
                hrLowSustainSec = (c.hrLowSustainMs / 1000L).toInt(),
                stillnessSec = (c.stillnessMs / 1000L).toInt(),
            )
        }

        val DEFAULT = forSensitivity(Sensitivity.MEDIUM)
    }
}
