package com.imagipoint.watch.gestures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verifies the pinned TFLite runtime can load and execute the bundled model on Android.
 * This does not assert gesture accuracy or preprocessing quality; those require labelled
 * recordings from the target watch.
 */
@RunWith(AndroidJUnit4::class)
class GestureModelInstrumentedTest {
    @Test
    fun bundledModelLoadsAndRunsOnAndroidRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loaded = GestureModelLoader.load(context)
        assertTrue("Model did not load: $loaded", loaded is ModelLoadResult.Ready)
        val model = (loaded as ModelLoadResult.Ready).model

        model.use {
            val input = ByteBuffer.allocateDirect(1 * 200 * 6 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(1 * 11 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            it.interpreter.run(input, output)

            output.rewind()
            val probabilities = FloatArray(11) { output.float }
            assertTrue(
                "Output must be finite probabilities: ${probabilities.toList()}",
                probabilities.all { value -> value.isFinite() && value in 0f..1f },
            )
            assertEquals(1.0, probabilities.sum().toDouble(), 0.02)
            assertTrue("Expected a non-degenerate softmax output", probabilities.max() > 0.05f)
        }
    }
}
