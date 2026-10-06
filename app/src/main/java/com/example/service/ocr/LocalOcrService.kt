package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fully on-device OCR. The bundled ML Kit recognizer receives only a local image
 * URI/file and never performs an application-level network request.
 */
class LocalOcrService(
    private val context: Context
) {
    suspend fun recognize(file: File): Result<String> {
        if (!file.exists() || !file.canRead()) {
            return Result.failure(IllegalArgumentException("OCR image is not readable."))
        }

        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val result = recognizer.process(image).await()
                Result.success(result.text.trim())
            } finally {
                recognizer.close()
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
