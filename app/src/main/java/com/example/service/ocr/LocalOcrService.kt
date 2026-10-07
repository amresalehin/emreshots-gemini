package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Fully on-device OCR powered by the powerful Tesseract OCR engine (supporting 25+ global languages)
 * and ML Kit. All processing is strictly local and private without network dependency during recognition.
 */
class LocalOcrService(
    private val context: Context,
    val tessDataManager: TessDataManager = TessDataManager(context)
) {
    suspend fun recognize(
        file: File,
        engine: String = "tesseract",
        languageCode: String = "eng"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            return@withContext Result.failure(IllegalArgumentException("OCR image is not readable."))
        }

        if (engine.equals("mlkit", ignoreCase = true)) {
            return@withContext recognizeWithMlKit(file)
        }

        // Tesseract engine
        recognizeWithTesseract(file, languageCode)
    }

    private suspend fun recognizeWithTesseract(file: File, languageCode: String): Result<String> {
        return try {
            tessDataManager.ensureBundledData()

            // Resolve effective language code(s)
            val requestedCodes = languageCode.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            val availableCodes = requestedCodes.filter { tessDataManager.isLanguageInstalled(it) }
            val effectiveCode = when {
                availableCodes.isNotEmpty() -> availableCodes.joinToString("+")
                tessDataManager.isLanguageInstalled("eng") -> "eng"
                tessDataManager.getInstalledLanguages().isNotEmpty() -> tessDataManager.getInstalledLanguages().first()
                else -> {
                    // Fall back to ML Kit if no Tesseract models are present yet
                    return recognizeWithMlKit(file)
                }
            }

            val bitmap = decodeOptimizedBitmap(file)
                ?: return Result.failure(IllegalArgumentException("Could not decode image for OCR."))

            val baseApi = TessBaseAPI()
            val initSuccess = baseApi.init(tessDataManager.dataPath, effectiveCode)
            if (!initSuccess) {
                baseApi.recycle()
                bitmap.recycle()
                // Graceful fallback to ML Kit if initialization fails
                return recognizeWithMlKit(file)
            }

            try {
                baseApi.setImage(bitmap)
                val rawText = baseApi.utF8Text.orEmpty().trim()
                Result.success(rawText)
            } finally {
                baseApi.stop()
                baseApi.recycle()
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (t: Throwable) {
            // If Tesseract throws an error (e.g. native lib issue in tests), fallback to ML Kit
            try {
                recognizeWithMlKit(file)
            } catch (_: Throwable) {
                Result.failure(t)
            }
        }
    }

    private suspend fun recognizeWithMlKit(file: File): Result<String> {
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

    private fun decodeOptimizedBitmap(file: File): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            // Downsample only if image is excessively large (> 3000px on max dimension)
            // while preserving sufficient sharpness for OCR character edges.
            val maxDimension = max(width, height)
            var sampleSize = 1
            while (maxDimension / sampleSize > 3072) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            // Correct EXIF orientation if needed
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }

            bitmap
        } catch (_: Throwable) {
            null
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
