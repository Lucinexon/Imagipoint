package com.imagipoint.watch.gestures

import org.json.JSONArray
import org.json.JSONObject

/** Result of validating the bundled labels/metadata against the known training contract. */
data class ModelContract(
    val labels: List<String>,
    val windowSamples: Int,
    val channels: Int,
    val samplingRateHz: Int,
    val offsetSamples: Int,
) {
    companion object {
        val EXPECTED = ModelContract(
            labels = GestureLabels.VERIFIED,
            windowSamples = 200,
            channels = 6,
            samplingRateHz = 200,
            offsetSamples = 50,
        )
    }
}

sealed class ContractCheck {
    data class Valid(val contract: ModelContract) : ContractCheck()
    data class Invalid(val reason: String) : ContractCheck()
}

/**
 * Strict validation of the metadata shipped with the supplied model. Nothing is guessed:
 * channel order, window size, sample rate, offset, label order and embedded normalization
 * must all match the recovered training metadata.
 */
object ModelContractVerifier {
    const val EXPECTED_SHA256 = "22c8e1141ea279d25d200543f8a7e0a69c2db923eb5023d2a09712af25a76bb2"
    private val EXPECTED_CHANNELS = listOf(
        "accel_x", "accel_y", "accel_z", "gyro_x", "gyro_y", "gyro_z",
    )

    fun verify(metadataJson: String, labelsJson: String): ContractCheck {
        return try {
            val metadata = JSONObject(metadataJson)
            val labels = parseLabels(labelsJson)
            if (labels.isEmpty()) return ContractCheck.Invalid("Label list is empty.")
            if (labels != GestureLabels.VERIFIED) {
                return ContractCheck.Invalid("Label order does not match the verified model output order.")
            }
            if (!metadata.optBoolean("normalization_embedded_in_model", false)) {
                return ContractCheck.Invalid("Metadata does not confirm embedded normalization.")
            }
            val channelOrder = metadata.optJSONArray("channel_order") ?: return ContractCheck.Invalid(
                "Missing channel_order metadata.",
            )
            if (channelOrder.toStringList() != EXPECTED_CHANNELS) {
                return ContractCheck.Invalid("Sensor channel order is not the verified accel/gyro order.")
            }
            val shape = metadata.optJSONArray("input_shape_per_window")
                ?: return ContractCheck.Invalid("Missing input shape metadata.")
            val shapeList = shape.toIntList()
            if (shapeList != listOf(200, 6)) {
                return ContractCheck.Invalid("Window shape is not [200, 6].")
            }
            if (metadata.optInt("dataset_sampling_rate_hz", -1) != 200) {
                return ContractCheck.Invalid("Metadata does not specify the expected 200 Hz rate.")
            }
            if (metadata.optInt("window_offset_samples", -1) != 50) {
                return ContractCheck.Invalid("Metadata does not specify the verified 50-sample hop.")
            }
            val classNames = metadata.optJSONArray("class_names_in_output_index_order")
                ?: return ContractCheck.Invalid("Missing output label metadata.")
            if (classNames.toStringList() != labels) {
                return ContractCheck.Invalid("Metadata labels and labels.json disagree.")
            }
            ContractCheck.Valid(
                ModelContract(labels, 200, 6, 200, 50),
            )
        } catch (t: Throwable) {
            ContractCheck.Invalid("Model metadata could not be parsed.")
        }
    }

    fun validateTensorShapes(inputShape: IntArray, outputShape: IntArray, labelCount: Int): String? {
        if (!inputShape.contentEquals(intArrayOf(1, 200, 6))) {
            return "Unexpected model input shape: ${inputShape.contentToString()}."
        }
        if (!outputShape.contentEquals(intArrayOf(1, labelCount))) {
            return "Unexpected model output shape: ${outputShape.contentToString()}."
        }
        return null
    }

    private fun parseLabels(json: String): List<String> = JSONArray(json).toStringList()

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { index -> optString(index) }

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).map { index -> optInt(index, -1) }
}
