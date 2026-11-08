package com.samibi.stayawake.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionOnlyDetectorTest {

    private val cfg = MotionConfig(stillnessMs = 45_000L, confirmMs = 10_000L, motionThreshold = 0.08)

    private fun detector(): MotionOnlyDetector =
        MotionOnlyDetector(cfg).also { it.start(0L) }

    @Test
    fun `no event while the wrist keeps moving`() {
        val d = detector()
        var t = 0L
        repeat(30) {
            t += 5_000L
            d.onMotion(t, 0.5)
            assertEquals(MotionEvent.NONE, d.tick(t))
        }
    }

    @Test
    fun `warns after the stillness window with no movement`() {
        val d = detector()
        assertEquals(MotionEvent.NONE, d.tick(44_000L))
        assertEquals(MotionEvent.WARN, d.tick(45_000L))
    }

    @Test
    fun `alarms when no movement follows the warning`() {
        val d = detector()
        assertEquals(MotionEvent.WARN, d.tick(45_000L))
        assertEquals(MotionEvent.NONE, d.tick(54_000L))
        assertEquals(MotionEvent.ALARM, d.tick(55_000L))
    }

    @Test
    fun `movement during the warning cancels it and restarts the stillness window`() {
        val d = detector()
        assertEquals(MotionEvent.WARN, d.tick(45_000L))
        d.onMotion(50_000L, 0.3)
        assertEquals(MotionEvent.NONE, d.tick(55_000L))
        assertEquals(MotionEvent.NONE, d.tick(94_000L))
        assertEquals(MotionEvent.WARN, d.tick(95_000L))
    }

    @Test
    fun `motion below the threshold does not count as movement`() {
        val d = detector()
        d.onMotion(30_000L, 0.02)
        assertEquals(MotionEvent.WARN, d.tick(45_000L))
    }

    @Test
    fun `warn fires once, not on every tick`() {
        val d = detector()
        assertEquals(MotionEvent.WARN, d.tick(45_000L))
        assertEquals(MotionEvent.NONE, d.tick(50_000L))
    }

    @Test
    fun `after an alarm the stillness window restarts`() {
        val d = detector()
        d.tick(45_000L)
        assertEquals(MotionEvent.ALARM, d.tick(55_000L))
        assertEquals(MotionEvent.NONE, d.tick(60_000L))
        assertEquals(MotionEvent.NONE, d.tick(99_000L))
        assertEquals(MotionEvent.WARN, d.tick(100_000L))
    }

    @Test
    fun `stillForMs reports time since last movement`() {
        val d = detector()
        d.onMotion(10_000L, 0.3)
        assertEquals(25_000L, d.stillForMs(35_000L))
    }
}
