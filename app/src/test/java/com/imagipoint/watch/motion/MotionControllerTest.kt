package com.imagipoint.watch.motion

import com.imagipoint.watch.settings.AppSettings
import com.imagipoint.watch.settings.Handedness
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionControllerTest {
    private val settings = AppSettings(smoothingMs = 0, deadZonePxPerSecond = 0f)
    private val startNs = 1_000_000_000L

    private fun quaternionYaw(angle: Float): FloatArray =
        floatArrayOf(0f, 0f, sin(angle / 2f), cos(angle / 2f))

    private fun quaternionPitch(angle: Float): FloatArray =
        floatArrayOf(sin(angle / 2f), 0f, 0f, cos(angle / 2f))

    /** Establishes a baseline and runs out the 320 ms warm-up at a 100 Hz sample rate. */
    private fun warmedController(controller: MotionController = MotionController()): MotionController {
        var t = startNs
        repeat(42) {
            controller.process(floatArrayOf(0f, 0f, 0f, 1f), t, settings)
            t += 10_000_000L
        }
        return controller
    }

    @Test
    fun firstSampleOnlyEstablishesBaseline() {
        val frame = MotionController().process(floatArrayOf(0f, 0f, 0f, 1f), startNs, settings)
        assertTrue(frame.valid)
        assertFalse(frame.output.hasMovement)
    }

    @Test
    fun warmUpSuppressesMovement() {
        val controller = MotionController()
        controller.process(floatArrayOf(0f, 0f, 0f, 1f), startNs, settings)
        val frame = controller.process(quaternionYaw(-0.2f), startNs + 10_000_000L, settings)
        assertTrue(frame.valid)
        assertFalse(frame.output.hasMovement)
    }

    @Test
    fun rightwardArmChangeMovesCursorRight() {
        val controller = warmedController()
        val frame = controller.process(quaternionYaw(-0.2f), startNs + 500_000_000L, settings)
        assertTrue(frame.valid)
        assertTrue("dx=${frame.output.dx}", frame.output.dx > 0)
        assertEquals(0, frame.output.dy)
    }

    @Test
    fun upwardArmChangeMovesCursorUp() {
        val controller = warmedController()
        val frame = controller.process(quaternionPitch(0.2f), startNs + 500_000_000L, settings)
        assertTrue(frame.output.dy < 0)
        assertEquals(0, frame.output.dx)
    }

    @Test
    fun leftHandednessProducesOppositeHorizontalDirection() {
        val controller = warmedController()
        val left = settings.copy(handedness = Handedness.LEFT)
        val frame = controller.process(quaternionYaw(-0.2f), startNs + 500_000_000L, left)
        assertTrue(frame.output.dx < 0)
    }

    @Test
    fun outlierRebaselinesInsteadOfJumping() {
        val controller = warmedController()
        val frame = controller.process(quaternionYaw(-1.5f), startNs + 500_000_000L, settings)
        assertTrue(frame.valid)
        assertFalse(frame.output.hasMovement)
        val next = controller.process(quaternionYaw(-1.51f), startNs + 510_000_000L, settings)
        assertTrue(next.valid)
        assertFalse(next.output.hasMovement)
    }

    @Test
    fun longGapRebaselines() {
        val controller = warmedController()
        val afterGap = controller.process(
            quaternionYaw(-0.2f),
            startNs + 900_000_000L,
            settings,
        )
        assertFalse(afterGap.output.hasMovement)
        assertEquals(0f, afterGap.angularSpeedRadPerSec)
    }

    @Test
    fun pausedAndCalibratingSuppressOutput() {
        val controller = warmedController()
        controller.setPaused(true)
        controller.process(quaternionYaw(-0.2f), startNs + 500_000_000L, settings)
        val paused = controller.process(quaternionYaw(-0.3f), startNs + 510_000_000L, settings)
        assertFalse(paused.output.hasMovement)

        controller.setPaused(false)
        controller.process(quaternionYaw(-0.3f), startNs + 600_000_000L, settings)
        controller.setCalibrating(true)
        val calibrating = controller.process(
            quaternionYaw(-0.4f),
            startNs + 610_000_000L,
            settings,
        )
        assertFalse(calibrating.output.hasMovement)
    }

    @Test
    fun recenterDropsBaselineAndSuppressesNextMove() {
        val controller = warmedController()
        val moving = controller.process(quaternionYaw(-0.2f), startNs + 420_000_000L, settings)
        assertTrue(moving.output.hasMovement)
        controller.recenter()
        val after = controller.process(quaternionYaw(-0.25f), startNs + 430_000_000L, settings)
        assertFalse(after.output.hasMovement)
    }

    @Test
    fun nearVerticalPointingIsRejected() {
        val controller = MotionController()
        var t = startNs
        repeat(40) {
            controller.process(quaternionPitch(1.5f), t, settings)
            t += 10_000_000L
        }
        val frame = controller.process(quaternionPitch(1.51f), t, settings)
        assertFalse(frame.valid)
    }

    @Test
    fun invalidQuaternionIsRejectedWithoutCrash() {
        val frame = MotionController().process(
            floatArrayOf(Float.NaN, 0f, 0f, 1f),
            startNs,
            settings,
        )
        assertFalse(frame.valid)
        assertEquals(0f, frame.headingRad)
    }

    @Test
    fun identicalSamplesProduceNoMovementAfterWarmUp() {
        val controller = warmedController()
        var t = startNs + 500_000_000L
        repeat(20) {
            val frame = controller.process(floatArrayOf(0f, 0f, 0f, 1f), t, settings)
            assertFalse(frame.output.hasMovement)
            t += 10_000_000L
        }
    }
}
