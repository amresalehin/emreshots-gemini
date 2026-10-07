package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
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
import kotlin.math.roundToInt

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

            val lowQuality = minOf(bitmap.width, bitmap.height) < 720 || maxOf(bitmap.width, bitmap.height) < 1280
            val enhancedBitmap = if (lowQuality) prepareLowQualityBitmap(bitmap) else null

            val baseApi = TessBaseAPI()
            val initSuccess = baseApi.init(tessDataManager.dataPath, effectiveCode)
            if (!initSuccess) {
                baseApi.recycle()
                enhancedBitmap?.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
                // Graceful fallback to ML Kit if initialization fails
                return recognizeWithMlKit(file)
            }

            try {
                // Low-resolution camera photos benefit substantially from rescaling and
                // grayscale/contrast normalization before Tesseract sees the pixels.
                val primaryBitmap = enhancedBitmap ?: bitmap
                baseApi.setImage(primaryBitmap)
                val primaryText = baseApi.utF8Text.orEmpty().trim()

                // If preprocessing produced no useful text, retry the original pixels before
                // falling back to ML Kit. This preserves accuracy for unusual colour layouts.
                val rawText = if (primaryText.length >= 3 || enhancedBitmap == null) {
                    primaryText
                } else {
                    baseApi.setImage(bitmap)
                    baseApi.utF8Text.orEmpty().trim()
                }
                Result.success(rawText)
            } finally {
                baseApi.stop()
                baseApi.recycle()
                if (enhancedBitmap != null && !enhancedBitmap.isRecycled) enhancedBitmap.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
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

    /**
     * Preprocess genuinely low-resolution photos for OCR. Tesseract documentation recommends
     * rescaling and image-quality preprocessing when character edges are too small/noisy.
     */
    private fun prepareLowQualityBitmap(source: Bitmap): Bitmap {
        val maxDimension = maxOf(source.width, source.height)
        val targetMax = 1800
        val scale = if (maxDimension < targetMax) targetMax.toFloat() / maxDimension else 1f
        val width = (source.width * scale).roundToInt().coerceAtLeast(source.width)
        val height = (source.height * scale).roundToInt().coerceAtLeast(source.height)

        val scaled = if (width != source.width || height != source.height) {
            Bitmap.createScaledBitmap(source, width, height, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val enhanced = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                1.35f, 0f, 0f, 0f, -45f,
                0f, 1.35f, 0f, 0f, -45f,
                0f, 0f, 1.35f, 0f, -45f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        return enhanced
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
