package com.imagipoint.watch.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTrackerTest {
    private val startNs = 1_000_000_000L

    @Test
    fun stillWristBecomesStableAfterMinimumWindow() {
        val tracker = CalibrationTracker()
        var result = CalibrationTracker.Result.COLLECTING
        for (i in 0 until 100) {
            result = tracker.add(0.01f, 0.02f, startNs + i * 10_000_000L)
            if (result == CalibrationTracker.Result.STABLE) break
        }
        assertEquals(CalibrationTracker.Result.STABLE, result)
        assertTrue(tracker.progress >= 0.3f)
    }

    @Test
    fun progressTracksStableWindowNotTimeout() {
        val tracker = CalibrationTracker()
        repeat(45) { i ->
            tracker.add(0f, 0f, startNs + i * 10_000_000L)
        }
        assertTrue("progress=${tracker.progress}", tracker.progress in 0.4f..0.6f)
    }

    @Test
    fun driftingWristTimesOut() {
        val tracker = CalibrationTracker()
        var result = CalibrationTracker.Result.COLLECTING
        for (i in 0 until 600) {
            val heading = if (i % 2 == 0) 0.1f else -0.1f
            result = tracker.add(heading, 0f, startNs + i * 10_000_000L)
            if (result == CalibrationTracker.Result.TIMED_OUT) break
        }
        assertEquals(CalibrationTracker.Result.TIMED_OUT, result)
    }

    @Test
    fun nonFiniteInputIsIgnored() {
        val tracker = CalibrationTracker()
        assertEquals(
            CalibrationTracker.Result.COLLECTING,
            tracker.add(Float.NaN, 0f, startNs),
        )
        assertEquals(0f, tracker.progress)
    }

    @Test
    fun resetClearsHistory() {
        val tracker = CalibrationTracker()
        repeat(40) { i -> tracker.add(0f, 0f, startNs + i * 10_000_000L) }
        tracker.reset()
        assertEquals(0f, tracker.progress)
        assertEquals(
            CalibrationTracker.Result.COLLECTING,
            tracker.add(0f, 0f, 2_000_000_000L),
        )
    }
}
