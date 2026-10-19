package com.imagipoint.watch.gestures

/** Fixed-capacity, allocation-free ring buffer for timestamped 3-axis sensor samples. */
class SensorRingBuffer(capacity: Int = 512) {
    private val capacity = capacity.coerceAtLeast(8)
    private val timestampsNs = LongArray(this.capacity)
    private val values = FloatArray(this.capacity * 3)
    private var head = 0
    private var size = 0
    private var rejected = 0L

    val count: Int get() = size
    val rejectedSamples: Long get() = rejected

    fun clear() {
        head = 0
        size = 0
        rejected = 0
    }

    fun latestTimestampNs(): Long? = if (size == 0) null else timestampsNs[physical(size - 1)]

    fun oldestTimestampNs(): Long? = if (size == 0) null else timestampsNs[physical(0)]

    fun add(timestampNs: Long, x: Float, y: Float, z: Float): Boolean {
        if (timestampNs <= 0L || !x.isFinite() || !y.isFinite() || !z.isFinite()) {
            rejected++
            return false
        }
        val latest = latestTimestampNs()
        if (latest != null && timestampNs <= latest) {
            rejected++
            return false
        }
        val index = head
        timestampsNs[index] = timestampNs
        values[index * 3] = x
        values[index * 3 + 1] = y
        values[index * 3 + 2] = z
        head = (head + 1) % capacity
        if (size < capacity) size++ else rejected++
        return true
    }

    /** Average sample rate over the most recent up-to-64 intervals. */
    fun rateHz(): Float {
        if (size < 2) return 0f
        val intervals = minOf(size - 1, 64)
        val newest = latestTimestampNs() ?: return 0f
        val oldestIndex = size - 1 - intervals
        val oldest = timestampsNs[physical(oldestIndex)]
        val duration = newest - oldest
        if (duration <= 0L) return 0f
        return intervals * 1_000_000_000f / duration
    }

    /**
     * Linearly interpolates at [targetNs] into out[offset..offset+2], multiplying by [scale].
     * Returns false when the target is outside the buffered range.
     */
    fun interpolateInto(
        targetNs: Long,
        out: FloatArray,
        offset: Int,
        scale: Float = 1f,
    ): Boolean {
        if (size == 0) return false
        val oldest = timestampsNs[physical(0)]
        val newest = timestampsNs[physical(size - 1)]
        if (targetNs < oldest || targetNs > newest) return false
        if (size == 1) {
            val base = physical(0) * 3
            out[offset] = values[base] * scale
            out[offset + 1] = values[base + 1] * scale
            out[offset + 2] = values[base + 2] * scale
            return true
        }
        var low = 0
        var high = size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (timestampsNs[physical(mid)] <= targetNs) low = mid else high = mid - 1
        }
        val indexA = low
        val timeA = timestampsNs[physical(indexA)]
        val baseA = physical(indexA) * 3
        if (timeA == targetNs || indexA == size - 1) {
            out[offset] = values[baseA] * scale
            out[offset + 1] = values[baseA + 1] * scale
            out[offset + 2] = values[baseA + 2] * scale
            return true
        }
        val indexB = indexA + 1
        val timeB = timestampsNs[physical(indexB)]
        val baseB = physical(indexB) * 3
        if (timeB <= timeA) return false
        val t = (targetNs - timeA).toFloat() / (timeB - timeA).toFloat()
        out[offset] = (values[baseA] + (values[baseB] - values[baseA]) * t) * scale
        out[offset + 1] = (values[baseA + 1] + (values[baseB + 1] - values[baseA + 1]) * t) * scale
        out[offset + 2] = (values[baseA + 2] + (values[baseB + 2] - values[baseA + 2]) * t) * scale
        return true
    }

    fun copyValuesAt(logicalIndex: Int, out: FloatArray, offset: Int): Boolean {
        if (logicalIndex !in 0 until size) return false
        val base = physical(logicalIndex) * 3
        out[offset] = values[base]
        out[offset + 1] = values[base + 1]
        out[offset + 2] = values[base + 2]
        return true
    }

    fun timestampAt(logicalIndex: Int): Long? =
        if (logicalIndex in 0 until size) timestampsNs[physical(logicalIndex)] else null

    private fun physical(logicalIndex: Int): Int =
        (head - size + logicalIndex + capacity * 2) % capacity
}
