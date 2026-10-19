package com.imagipoint.watch.gestures

import kotlin.math.min

/**
 * Fourth-order Butterworth bandpass (30-90 Hz at 200 Hz), matching the HGAG-DATA
 * preprocessing description. Coefficients were designed independently with
 * scipy.signal.butter(4, [30, 90], btype="bandpass", fs=200).
 *
 * The supplied metadata does not include the original training filter implementation,
 * so this class performs deterministic zero-phase forward/backward filtering with
 * reflection padding. It is an honest reconstruction, not a claim of bit-identical
 * training preprocessing.
 */
class ButterworthBandpass private constructor(
    private val sections: Array<DoubleArray>,
    private val padding: Int,
) {
    private var padded = DoubleArray(0)
    private var forward = DoubleArray(0)
    private var channel = DoubleArray(0)

    /**
     * Filters [samples] in place. Layout is time-major: sample * [CHANNELS] + channel.
     * All work is done on pre-allocated scratch arrays.
     */
    fun filterInPlace(samples: FloatArray, sampleCount: Int, channels: Int = CHANNELS) {
        val required = sampleCount * channels
        require(samples.size >= required)
        ensureCapacity(sampleCount)
        for (c in 0 until channels) {
            for (i in 0 until sampleCount) {
                channel[i] = samples[i * channels + c].toDouble()
            }
            filterZeroPhase(channel, sampleCount)
            for (i in 0 until sampleCount) {
                samples[i * channels + c] = channel[i].toFloat()
            }
        }
    }

    private fun ensureCapacity(sampleCount: Int) {
        val paddedSize = sampleCount + 2 * padding
        if (padded.size < paddedSize) padded = DoubleArray(paddedSize)
        if (forward.size < paddedSize) forward = DoubleArray(paddedSize)
        if (channel.size < sampleCount) channel = DoubleArray(sampleCount)
    }

    private fun filterZeroPhase(src: DoubleArray, length: Int) {
        val p = padding
        val total = length + 2 * p
        for (j in 0 until p) {
            padded[p - 1 - j] = src[j]
            val rightIndex = (length - 2 - j).coerceIn(0, length - 1)
            padded[p + length + j] = src[rightIndex]
        }
        for (i in 0 until length) padded[p + i] = src[i]

        // Forward pass.
        for (section in sections) {
            var z1 = 0.0
            var z2 = 0.0
            val b0 = section[0]
            val b1 = section[1]
            val b2 = section[2]
            val a1 = section[4]
            val a2 = section[5]
            for (i in 0 until total) {
                val input = padded[i]
                val output = b0 * input + z1
                z1 = b1 * input - a1 * output + z2
                z2 = b2 * input - a2 * output
                forward[i] = output
            }
            forward.copyInto(padded, 0, 0, total)
        }

        // Backward pass over the forward result.
        for (section in sections) {
            var z1 = 0.0
            var z2 = 0.0
            val b0 = section[0]
            val b1 = section[1]
            val b2 = section[2]
            val a1 = section[4]
            val a2 = section[5]
            for (i in total - 1 downTo 0) {
                val input = padded[i]
                val output = b0 * input + z1
                z1 = b1 * input - a1 * output + z2
                z2 = b2 * input - a2 * output
                forward[i] = output
            }
            forward.copyInto(padded, 0, 0, total)
        }

        for (i in 0 until length) src[i] = padded[p + i]
    }

    companion object {
        const val CHANNELS = 6
        const val SAMPLE_RATE_HZ = 200

        private val COEFFICIENTS = arrayOf(
            doubleArrayOf(
                0.16717926860848994, -0.3343585372169799, 0.16717926860848994,
                1.0, -0.5418421547938043, 0.12428811601288457,
            ),
            doubleArrayOf(
                1.0, 2.0, 1.0,
                1.0, 1.4203516303076948, 0.5196607446342449,
            ),
            doubleArrayOf(
                1.0, -2.0, 1.0,
                1.0, -0.9066302244462777, 0.5744851222349661,
            ),
            doubleArrayOf(
                1.0, 2.0, 1.0,
                1.0, 1.7198734191690779, 0.811727845882331,
            ),
        )

        // scipy.signal.sosfiltfilt default padlen for four SOS sections.
        private const val PAD = 27

        fun create(): ButterworthBandpass = ButterworthBandpass(COEFFICIENTS, PAD)
    }
}
