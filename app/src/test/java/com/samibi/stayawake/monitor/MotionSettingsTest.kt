package com.samibi.stayawake.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionSettingsTest {

    @Test
    fun `defaults match the detector defaults`() {
        val cfg = MotionSettings.DEFAULT.toConfig()
        assertEquals(MotionConfig(), cfg)
    }

    @Test
    fun `toConfig converts seconds to ms and hundredths to threshold`() {
        val s = MotionSettings(stillnessSec = 30, confirmSec = 20, thresholdCenti = 12)
        val cfg = s.toConfig()
        assertEquals(30_000L, cfg.stillnessMs)
        assertEquals(20_000L, cfg.confirmMs)
        assertEquals(0.12, cfg.motionThreshold, 1e-9)
    }

    @Test
    fun `sensor timing is derived from window and sample rate`() {
        val s = MotionSettings(windowSec = 8, sampleHz = 4)
        assertEquals(8_000L, s.windowMs)
        assertEquals(250_000, s.samplePeriodUs)
        assertEquals(8_000_000, s.maxReportLatencyUs)
    }

    @Test
    fun `default sensor timing is 5s window at 5 Hz`() {
        val s = MotionSettings.DEFAULT
        assertEquals(5_000L, s.windowMs)
        assertEquals(200_000, s.samplePeriodUs)
        assertEquals(5_000_000, s.maxReportLatencyUs)
    }

    @Test
    fun `coerced clamps every field into its range`() {
        val low = MotionSettings(0, 0, 0, 0, 0).coerced()
        assertEquals(MotionSettings.STILL_MIN, low.stillnessSec)
        assertEquals(MotionSettings.CONFIRM_MIN, low.confirmSec)
        assertEquals(MotionSettings.THRESHOLD_MIN, low.thresholdCenti)
        assertEquals(MotionSettings.WINDOW_MIN, low.windowSec)
        assertEquals(MotionSettings.HZ_MIN, low.sampleHz)

        val high = MotionSettings(9999, 9999, 9999, 9999, 9999).coerced()
        assertEquals(MotionSettings.STILL_MAX, high.stillnessSec)
        assertEquals(MotionSettings.CONFIRM_MAX, high.confirmSec)
        assertEquals(MotionSettings.THRESHOLD_MAX, high.thresholdCenti)
        assertEquals(MotionSettings.WINDOW_MAX, high.windowSec)
        assertEquals(MotionSettings.HZ_MAX, high.sampleHz)
    }

    @Test
    fun `coerced leaves in-range values untouched`() {
        val s = MotionSettings(60, 15, 10, 6, 3)
        assertEquals(s, s.coerced())
    }
}
