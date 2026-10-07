package com.amresalehin.emreshots.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fully on-device OCR powered exclusively by Tesseract and traineddata language packs.
 *
 * OCR never selects an alternate engine: the selected Tesseract language(s) are used directly,
 * then the extracted text is passed to the local text-only LLM enrichment pipeline.
 */
class LocalOcrService(
    private val context: Context,
    val tessDataManager: TessDataManager = TessDataManager(context)
) {
    suspend fun recognize(
        file: File,
        languageCode: String = "eng"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            return@withContext Result.failure(IllegalArgumentException("OCR image is not readable."))
        }

        recognizeWithTesseract(file, languageCode)
    }

    private suspend fun recognizeWithTesseract(file: File, languageCode: String): Result<String> {
        return try {
            tessDataManager.ensureBundledData()

            val requestedCodes = languageCode.split("+")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val availableCodes = requestedCodes.filter { tessDataManager.isLanguageInstalled(it) }
            val effectiveCode = when {
                availableCodes.isNotEmpty() -> availableCodes.joinToString("+")
                tessDataManager.isLanguageInstalled("eng") -> "eng"
                tessDataManager.getInstalledLanguages().isNotEmpty() -> tessDataManager.getInstalledLanguages().first()
                else -> return Result.failure(IllegalStateException("No Tesseract traineddata language pack is installed."))
            }

            val bitmap = decodeOptimizedBitmap(file)
                ?: return Result.failure(IllegalArgumentException("Could not decode image for OCR."))

            val lowQuality = minOf(bitmap.width, bitmap.height) < 720 ||
                maxOf(bitmap.width, bitmap.height) < 1280
            val enhancedBitmap = if (lowQuality) prepareLowQualityBitmap(bitmap) else null

            val baseApi = TessBaseAPI()
            val initSuccess = baseApi.init(tessDataManager.dataPath, effectiveCode)
            if (!initSuccess) {
                baseApi.recycle()
                enhancedBitmap?.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
                return Result.failure(IllegalStateException("Tesseract could not initialize language data: $effectiveCode"))
            }

            val rawText = try {
                val primaryBitmap = enhancedBitmap ?: bitmap
                baseApi.setImage(primaryBitmap)
                val primaryText = baseApi.utF8Text.orEmpty().trim()

                // For difficult low-resolution photos, retry the original pixels before giving up.
                if (primaryText.length >= 3 || enhancedBitmap == null) {
                    primaryText
                } else {
                    baseApi.setImage(bitmap)
                    baseApi.utF8Text.orEmpty().trim()
                }
            } finally {
                baseApi.stop()
                baseApi.recycle()
                if (enhancedBitmap != null && !enhancedBitmap.isRecycled) enhancedBitmap.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
            }

            if (rawText.isBlank()) {
                Result.failure(IllegalStateException("Tesseract found no readable text."))
            } else {
                Result.success(rawText)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Preprocess genuinely low-resolution photos for OCR. Rescaling and grayscale/contrast
     * normalization improve character edges without introducing a second OCR engine.
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
