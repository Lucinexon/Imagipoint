package com.imagipoint.watch.gestures

/**
 * Requires a class to win consistently before allowing an action. A fired gesture must
 * disappear (or lose confidence) before it can fire again; this prevents held-hand gestures
 * from repeating, independent of the cooldown timer.
 */
class GestureStabilizer(
    private val requiredConsecutive: Int = 2,
    private val globalCooldownMs: Long = 650,
) {
    data class Decision(val label: String, val confidence: Float)

    private var candidateLabel: String? = null
    private var candidateCount = 0
    private var disarmedLabel: String? = null
    private var lastFireMs = NEVER_FIRED
    private val lastFireByLabel = HashMap<String, Long>()

    fun reset() {
        candidateLabel = null
        candidateCount = 0
        disarmedLabel = null
        lastFireMs = NEVER_FIRED
        lastFireByLabel.clear()
    }

    fun update(
        label: String?,
        confidence: Float,
        nowMs: Long,
        threshold: Float,
        cooldownMs: Long,
    ): Decision? {
        if (label == null || label !in GestureLabels.VERIFIED_SET || !confidence.isFinite()) {
            candidateLabel = null
            candidateCount = 0
            return null
        }
        if (disarmedLabel != null) {
            if (label != disarmedLabel || confidence < threshold) {
                disarmedLabel = null
            } else {
                // The same held gesture keeps the detector disarmed.
                return null
            }
        }
        if (confidence < threshold) {
            candidateLabel = null
            candidateCount = 0
            return null
        }
        if (label == candidateLabel) {
            candidateCount++
        } else {
            candidateLabel = label
            candidateCount = 1
        }
        if (candidateCount < requiredConsecutive) return null
        val lastForLabel = lastFireByLabel[label]
        if (lastForLabel != null && nowMs - lastForLabel < cooldownMs) return null
        if (lastFireMs != NEVER_FIRED && nowMs - lastFireMs < globalCooldownMs) return null
        lastFireMs = nowMs
        lastFireByLabel[label] = nowMs
        disarmedLabel = label
        candidateCount = 0
        return Decision(label, confidence)
    }

    companion object {
        private const val NEVER_FIRED = Long.MIN_VALUE
    }
}
