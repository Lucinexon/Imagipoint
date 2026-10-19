package com.imagipoint.watch.ui

import android.content.Context
import com.imagipoint.watch.gestures.ContractCheck
import com.imagipoint.watch.gestures.ModelContractVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Read-only integrity check used by the Diagnostics screen when no session is running. */
data class ModelIntegrityReport(
    val verified: Boolean,
    val sha256: String?,
    val labels: Int,
    val sizeBytes: Int,
    val reason: String?,
)

object ModelIntegrity {
    suspend fun verify(context: Context): ModelIntegrityReport = withContext(Dispatchers.IO) {
        try {
            val model = context.assets.open("hgag_gesture_model_float32.tflite").use { it.readBytes() }
            val metadata = context.assets.open("model_metadata.json").use { it.readBytes().decodeToString() }
            val labels = context.assets.open("labels.json").use { it.readBytes().decodeToString() }
            val digest = MessageDigest.getInstance("SHA-256").digest(model)
                .joinToString("") { "%02x".format(it) }
            if (digest != ModelContractVerifier.EXPECTED_SHA256) {
                return@withContext ModelIntegrityReport(
                    verified = false,
                    sha256 = digest,
                    labels = 0,
                    sizeBytes = model.size,
                    reason = "Model checksum does not match the verified release.",
                )
            }
            when (val check = ModelContractVerifier.verify(metadata, labels)) {
                is ContractCheck.Invalid -> ModelIntegrityReport(
                    verified = false,
                    sha256 = digest,
                    labels = 0,
                    sizeBytes = model.size,
                    reason = check.reason,
                )
                is ContractCheck.Valid -> ModelIntegrityReport(
                    verified = true,
                    sha256 = digest,
                    labels = check.contract.labels.size,
                    sizeBytes = model.size,
                    reason = null,
                )
            }
        } catch (t: Throwable) {
            ModelIntegrityReport(
                verified = false,
                sha256 = null,
                labels = 0,
                sizeBytes = 0,
                reason = t.message ?: "Bundled model could not be read.",
            )
        }
    }
}
