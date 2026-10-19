package com.imagipoint.watch.gestures

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAssetsTest {
    private val assetsDir: File = run {
        val working = File(System.getProperty("user.dir") ?: ".")
        val direct = File(working, "src/main/assets")
        if (direct.isDirectory) direct else File(working, "app/src/main/assets")
    }

    private fun asset(name: String): File = File(assetsDir, name)

    @Test
    fun bundledModelMatchesVerifiedChecksum() {
        val model = asset("hgag_gesture_model_float32.tflite")
        assertTrue("Missing model at ${model.absolutePath}", model.isFile)
        assertEquals(95_948L, model.length())
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(model.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(ModelContractVerifier.EXPECTED_SHA256, hash)
    }

    @Test
    fun bundledMetadataAndLabelsSatisfyContract() {
        val metadata = asset("model_metadata.json").readText()
        val labels = asset("labels.json").readText()
        val result = ModelContractVerifier.verify(metadata, labels)
        assertTrue("Contract rejected: $result", result is ContractCheck.Valid)
        val contract = (result as ContractCheck.Valid).contract
        assertEquals(11, contract.labels.size)
        assertEquals("Index Thumb Tap", contract.labels[6])
        assertEquals(200, contract.windowSamples)
        assertEquals(50, contract.offsetSamples)
    }

    @Test
    fun metadataMatchesGestureLabelsExactly() {
        val metadata = JSONObject(asset("model_metadata.json").readText())
        val classNames = metadata.getJSONArray("class_names_in_output_index_order")
        assertEquals(GestureLabels.VERIFIED.size, classNames.length())
        for (i in GestureLabels.VERIFIED.indices) {
            assertEquals(GestureLabels.VERIFIED[i], classNames.getString(i))
        }
    }

    @Test
    fun normalizationMetadataMatchesEmbeddedModelConstants() {
        val metadata = JSONObject(asset("model_metadata.json").readText())
        val mean = metadata.getJSONArray("normalization_mean_fit_on_training_subjects_only")
        val std = metadata.getJSONArray("normalization_std_fit_on_training_subjects_only")
        assertEquals(6, mean.length())
        assertEquals(6, std.length())
        assertEquals(1.1521964073181152, std.getDouble(0), 1e-9)
        assertEquals(0.09123071283102036, std.getDouble(5), 1e-9)
    }

    @Test
    fun tensorShapeValidationRejectsWrongContracts() {
        assertEquals(null, ModelContractVerifier.validateTensorShapes(intArrayOf(1, 200, 6), intArrayOf(1, 11), 11))
        assertTrue(
            ModelContractVerifier.validateTensorShapes(intArrayOf(1, 100, 6), intArrayOf(1, 11), 11) != null,
        )
        assertTrue(
            ModelContractVerifier.validateTensorShapes(intArrayOf(1, 200, 6), intArrayOf(1, 10), 11) != null,
        )
    }

    @Test
    fun strictContractRejectsChangedChannelOrder() {
        val metadata = JSONObject(asset("model_metadata.json").readText())
        metadata.put(
            "channel_order",
            JSONArray(listOf("gyro_x", "gyro_y", "gyro_z", "accel_x", "accel_y", "accel_z")),
        )
        val result = ModelContractVerifier.verify(metadata.toString(), asset("labels.json").readText())
        assertTrue(result is ContractCheck.Invalid)
    }

    @Test
    fun strictContractRejectsMissingNormalizationConfirmation() {
        val metadata = JSONObject(asset("model_metadata.json").readText())
        metadata.remove("normalization_embedded_in_model")
        val result = ModelContractVerifier.verify(metadata.toString(), asset("labels.json").readText())
        assertTrue(result is ContractCheck.Invalid)
    }

    @Test
    fun strictContractRejectsReorderedLabels() {
        val labels = JSONArray(asset("labels.json").readText())
        val reordered = JSONArray()
        for (i in labels.length() - 1 downTo 0) reordered.put(labels.getString(i))
        val result = ModelContractVerifier.verify(
            asset("model_metadata.json").readText(),
            reordered.toString(),
        )
        assertTrue(result is ContractCheck.Invalid)
    }

    @Test
    fun malformedMetadataFailsClosed() {
        val result = ModelContractVerifier.verify("{not json", asset("labels.json").readText())
        assertTrue(result is ContractCheck.Invalid)
    }
}
