package com.imagipoint.watch.gestures

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class VerifiedGestureModel(
    val interpreter: Interpreter,
    val labels: List<String>,
    val sha256: String,
) : Closeable {
    override fun close() {
        runCatching { interpreter.close() }
    }
}

sealed class ModelLoadResult {
    data class Ready(val model: VerifiedGestureModel) : ModelLoadResult()
    data class Unavailable(val reason: String) : ModelLoadResult()
}

/** Loads the bundled model only if checksum, labels, metadata and tensor contracts all verify. */
object GestureModelLoader {
    const val MODEL_ASSET = "hgag_gesture_model_float32.tflite"
    const val LABELS_ASSET = "labels.json"
    const val METADATA_ASSET = "model_metadata.json"

    fun load(context: Context): ModelLoadResult {
        var interpreter: Interpreter? = null
        return try {
            val metadata = context.assets.open(METADATA_ASSET).bufferedReader().use { it.readText() }
            val labels = context.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
            val check = ModelContractVerifier.verify(metadata, labels)
            if (check is ContractCheck.Invalid) {
                return ModelLoadResult.Unavailable(check.reason)
            }
            val contract = (check as ContractCheck.Valid).contract

            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val sha = sha256(modelBytes)
            if (sha != ModelContractVerifier.EXPECTED_SHA256) {
                return ModelLoadResult.Unavailable(
                    "Gesture model checksum does not match the verified binary.",
                )
            }
            val buffer = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .put(modelBytes)
                .apply { rewind() }
            // One CPU thread keeps gesture inference from stealing cycles from sensors/HID.
            interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(1))
            val inputDetails = interpreter.inputTensorCount
            val outputDetails = interpreter.outputTensorCount
            if (inputDetails != 1 || outputDetails != 1) {
                return ModelLoadResult.Unavailable("Gesture model must have one input and one output.")
            }
            val input = interpreter.getInputTensor(0)
            val output = interpreter.getOutputTensor(0)
            if (input.dataType() != DataType.FLOAT32 || output.dataType() != DataType.FLOAT32) {
                return ModelLoadResult.Unavailable("Gesture model is not float32.")
            }
            val shapeError = ModelContractVerifier.validateTensorShapes(
                input.shape(),
                output.shape(),
                contract.labels.size,
            )
            if (shapeError != null) return ModelLoadResult.Unavailable(shapeError)
            interpreter.allocateTensors()
            ModelLoadResult.Ready(VerifiedGestureModel(interpreter, contract.labels, sha))
        } catch (e: SecurityException) {
            interpreter?.close()
            ModelLoadResult.Unavailable("Gesture model could not be read.")
        } catch (t: Throwable) {
            runCatching { interpreter?.close() }
            ModelLoadResult.Unavailable("Gesture model failed to load.")
        }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
