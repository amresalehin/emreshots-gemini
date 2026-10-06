package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
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
    suspend fun recognize(file: File, languages: List<String> = listOf("Latin")): Result<String> {
        if (!file.exists() || !file.canRead()) {
            return Result.failure(IllegalArgumentException("OCR image is not readable."))
        }

        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val selected = languages.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Latin") }
            val recognizers = selected.mapNotNull { language ->
                when (language.lowercase()) {
                    "latin" -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    "chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    "devanagari" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                    "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                    "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                    else -> null
                }
            }
            if (recognizers.isEmpty()) return Result.failure(IllegalArgumentException("No supported OCR languages selected."))
            try {
                val results = recognizers.map { it.process(image).await().text.trim() }
                Result.success(results.filter { it.isNotBlank() }.distinct().joinToString("\n").trim())
            } finally {
                recognizers.forEach { runCatching { it.close() } }
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
