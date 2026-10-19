package com.imagipoint.watch.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStabilizerTest {
    @Test
    fun requiresTwoConsecutiveWins() {
        val stabilizer = GestureStabilizer()
        assertNull(stabilizer.update("Index Thumb Tap", 0.95f, 1_000L, 0.85f, 2_000L))
        assertNotNull(stabilizer.update("Index Thumb Tap", 0.95f, 1_250L, 0.85f, 2_000L))
    }

    @Test
    fun changingClassResetsCandidate() {
        val stabilizer = GestureStabilizer()
        stabilizer.update("Index Thumb Tap", 0.95f, 1_000L, 0.85f, 2_000L)
        stabilizer.update("Thumb Up", 0.95f, 1_250L, 0.85f, 2_000L)
        assertNull(stabilizer.update("Index Thumb Tap", 0.95f, 1_500L, 0.85f, 2_000L))
    }

    @Test
    fun lowConfidenceNeverFires() {
        val stabilizer = GestureStabilizer()
        repeat(10) { i ->
            assertNull(
                stabilizer.update(
                    "Index Thumb Tap",
                    0.70f,
                    1_000L + i * 250L,
                    0.85f,
                    2_000L,
                ),
            )
        }
    }

    @Test
    fun heldGestureDoesNotRepeatAndRearmsAfterRelease() {
        val stabilizer = GestureStabilizer()
        stabilizer.update("Index Thumb Tap", 0.95f, 1_000L, 0.85f, 2_000L)
        assertNotNull(stabilizer.update("Index Thumb Tap", 0.95f, 1_250L, 0.85f, 2_000L))
        // Still held: disarmed.
        repeat(5) { i ->
            assertNull(
                stabilizer.update(
                    "Index Thumb Tap",
                    0.99f,
                    1_500L + i * 250L,
                    0.85f,
                    2_000L,
                ),
            )
        }
        // Release, then form the gesture again after the per-label cooldown.
        assertNull(stabilizer.update("Index Thumb Tap", 0.30f, 3_500L, 0.85f, 2_000L))
        assertNull(stabilizer.update("Index Thumb Tap", 0.95f, 4_000L, 0.85f, 2_000L))
        assertNotNull(stabilizer.update("Index Thumb Tap", 0.95f, 4_250L, 0.85f, 2_000L))
    }

    @Test
    fun perLabelCooldownBlocksRapidRefire() {
        val stabilizer = GestureStabilizer()
        assertNull(stabilizer.update("Thumb Up", 0.95f, 1_000L, 0.85f, 2_000L))
        assertNotNull(stabilizer.update("Thumb Up", 0.95f, 1_250L, 0.85f, 2_000L))
        assertNull(stabilizer.update("Thumb Up", 0.30f, 1_500L, 0.85f, 2_000L))
        assertNull(stabilizer.update("Thumb Up", 0.95f, 1_750L, 0.85f, 2_000L))
        assertNull(stabilizer.update("Thumb Up", 0.95f, 2_000L, 0.85f, 2_000L))
        assertNotNull(stabilizer.update("Thumb Up", 0.95f, 3_250L, 0.85f, 2_000L))
    }

    @Test
    fun unknownLabelsAreIgnored() {
        val stabilizer = GestureStabilizer()
        repeat(4) { i ->
            assertNull(stabilizer.update("Made Up", 0.99f, 1_000L + i * 250L, 0.5f, 0L))
        }
    }

    @Test
    fun resetClearsCooldownHistory() {
        val stabilizer = GestureStabilizer()
        stabilizer.update("Thumb Up", 0.95f, 1_000L, 0.85f, 2_000L)
        assertNotNull(stabilizer.update("Thumb Up", 0.95f, 1_250L, 0.85f, 2_000L))
        stabilizer.reset()
        stabilizer.update("Thumb Up", 0.95f, 1_500L, 0.85f, 2_000L)
        assertNotNull(stabilizer.update("Thumb Up", 0.95f, 1_750L, 0.85f, 2_000L))
    }
}
