package com.amresalehin.emreshots.service.ocr

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocalOcrService(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build(),
) {
    data class OcrLanguage(val name: String, val code: String, val description: String)

    companion object {
        val supportedLanguages = listOf(
            OcrLanguage("English", "eng", "English"),
            OcrLanguage("Chinese", "chi_sim", "Mandarin / Simplified Chinese"),
            OcrLanguage("Hindi", "hin", "Hindi"),
            OcrLanguage("Spanish", "spa", "Spanish"),
            OcrLanguage("French", "fra", "French"),
            OcrLanguage("Arabic", "ara", "Arabic"),
            OcrLanguage("Bengali", "ben", "Bengali"),
            OcrLanguage("Portuguese", "por", "Portuguese"),
            OcrLanguage("Indonesian", "ind", "Indonesian"),
            OcrLanguage("Urdu", "urd", "Urdu"),
        )
        private const val TESSDATA_BASE = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"
    }

    private val dataRoot = File(context.filesDir, "tesseract").apply { mkdirs() }
    private val tessdata = File(dataRoot, "tessdata").apply { mkdirs() }

    suspend fun recognize(file: File, languages: List<String> = listOf("English")): Result<String> =
        withContext(Dispatchers.IO) {
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("OCR image is not readable."))
            }
            val selected = languages
                .mapNotNull { value -> supportedLanguages.firstOrNull { it.name.equals(value, true) || it.code.equals(value, true) } }
                .distinctBy { it.code }
                .ifEmpty { listOf(supportedLanguages.first()) }
            try {
                selected.forEach { ensureLanguagePack(it) }
                val sections = selected.mapNotNull { language ->
                    recognizeWithLanguage(file, language).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                        ?.let { "[${language.name}]\n$it" }
                }
                if (sections.isEmpty()) Result.failure(IllegalStateException("No text was detected for the selected languages."))
                else Result.success(sections.joinToString("\n\n").trim())
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun recognizeWithLanguage(file: File, language: OcrLanguage): Result<String> {
        val tess = TessBaseAPI()
        return try {
            check(tess.init(dataRoot.absolutePath, language.code)) {
                "Unable to initialize local OCR for ${language.name}."
            }
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD)
            tess.setImage(file)
            Result.success(tess.getUTF8Text().orEmpty())
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            runCatching { tess.recycle() }
        }
    }

    private fun ensureLanguagePack(language: OcrLanguage) {
        val target = File(tessdata, "${language.code}.traineddata")
        if (target.isFile && target.length() > 0L) return
        val partial = File(tessdata, "${language.code}.traineddata.part")
        val request = Request.Builder().url(TESSDATA_BASE + language.code + ".traineddata").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "Could not download ${language.name} OCR language pack (HTTP ${response.code})."
            }
            val body = response.body ?: error("Empty ${language.name} OCR language pack response.")
            partial.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
        }
        check(partial.length() > 0L) { "Downloaded ${language.name} OCR language pack is empty." }
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Could not install ${language.name} OCR language pack." }
    }

    fun normalizeLanguages(values: List<String>): List<String> =
        values.mapNotNull { raw ->
            val normalized = raw.trim().lowercase(Locale.US)
            supportedLanguages.firstOrNull { it.name.lowercase(Locale.US) == normalized || it.code == normalized }?.name
        }.distinct().ifEmpty { listOf("English") }
}
