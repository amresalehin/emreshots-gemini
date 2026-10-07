package com.amresalehin.emreshots.service.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device screenshot AI processing engine (inspired by PixelShot / Pixel Screenshots).
 *
 * Directly receives raw extracted OCR text from Tesseract or ML Kit, repairs scanning artefacts,
 * fixes character misreads/hyphens/spacing, and automatically assigns relevant categorization tags.
 *
 * Uses:
 * 1. An on-device small predownloaded text LLM (text-only, NO VLM overhead),
 * 2. Specialized algorithmic OCR repair and semantic keyword classification pipeline.
 */
class OcrArtefactLlmFixer(
    private val context: Context,
    val localLlmManager: LocalOcrLlmManager = LocalOcrLlmManager(context)
) {
    /**
     * Primary entry point: Processes raw OCR text directly through local AI,
     * returning fixed OCR text, categorization tags, detected links, and title.
     */
    suspend fun processOcrWithLocalAi(rawText: String): LocalOcrAiResult = withContext(Dispatchers.IO) {
        if (rawText.isBlank() || rawText.length < 3) {
            return@withContext LocalOcrAiResult(fixedOcrText = rawText.trim())
        }

        // 1. Run inference using the small Local text LLM (pure text, no VLM)
        val llmResult = localLlmManager.processOcrWithLocalLlm(rawText)
        if (llmResult != null && llmResult.fixedOcrText.isNotBlank()) {
            val cleanedText = cleanupOcrArtefactsRuleBased(llmResult.fixedOcrText)
            val semanticTags = extractSemanticTags(cleanedText)
            val mergedTags = (llmResult.tags + semanticTags).distinct()
            val links = (llmResult.detectedLinks + extractDetectedLinks(cleanedText)).distinct()
            val title = llmResult.title ?: extractSalientTitle(cleanedText)

            return@withContext LocalOcrAiResult(
                fixedOcrText = cleanedText,
                tags = mergedTags,
                title = title,
                detectedLinks = links
            )
        }

        // 2. Deterministic on-device fallback (guaranteed in tests and instant fallback)
        val cleaned = cleanupOcrArtefactsRuleBased(rawText)
        val tags = extractSemanticTags(cleaned)
        val links = extractDetectedLinks(cleaned)
        val title = extractSalientTitle(cleaned)

        LocalOcrAiResult(
            fixedOcrText = cleaned,
            tags = tags,
            title = title,
            detectedLinks = links
        )
    }

    /**
     * Backward-compatible method to return just the cleaned OCR text.
     */
    suspend fun fixArtefacts(rawText: String): String = withContext(Dispatchers.IO) {
        processOcrWithLocalAi(rawText).fixedOcrText
    }

    /**
     * Rule-based algorithmic repair for common OCR scanning artefacts.
     */
    fun cleanupOcrArtefactsRuleBased(text: String): String {
        var result = text

        // 1. Fix words hyphenated across line breaks: e.g. "con- \n tent" -> "content"
        result = result.replace(Regex("([a-zA-Z]{2,})-[ \\t]*\\r?\\n[ \\t]*([a-zA-Z]{2,})"), "$1$2")

        // 2. Fix broken protocol URLs and split domain/paths: e.g. "https : / / github . com" -> "https://github.com"
        result = result.replace(Regex("(https?)[ \\t]*:[ \\t]*/[ \\t]*/[ \\t]*"), "$1://")
        result = result.replace(Regex("www[ \\t]*\\.[ \\t]*"), "www.")
        result = result.replace(Regex("((?:https?://|www\\.)[a-zA-Z0-9_-]+)[ \\t]*\\.[ \\t]*([a-zA-Z0-9_.-]+)"), "$1.$2")
        result = result.replace(Regex("((?:https?://|www\\.)[^\\s]+?)[ \\t]*/[ \\t]*([a-zA-Z0-9_.-]+)"), "$1/$2")

        // 3. Fix space before punctuation marks: e.g. "hello , world" -> "hello, world"
        result = result.replace(Regex("[ \\t]+([,.:;?!%])"), "$1")

        // 4. Fix split contractions and apostrophes: e.g. "don ' t" -> "don't"
        result = result.replace(Regex("([a-zA-Z0-9])[ \\t]*['’][ \\t]*([a-zA-Z0-9])"), "$1'$2")

        // 5. Remove common stray scanning border noise lines (isolated lone symbols like ~ or _ or |)
        result = result.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.length in 1..2 && trimmed.all { it in "~`-_+=|/\\^*#•·" }
            }
            .joinToString("\n")

        // 6. Fix excessive empty lines (more than 2 consecutive newlines)
        result = result.replace(Regex("\\n{3,}"), "\n\n")

        return result.trim()
    }

    /**
     * Semantic tag generator (PixelShot style).
     * Automatically extracts relevant classification tags from the OCR transcript.
     */
    fun extractSemanticTags(text: String): List<String> {
        val tags = mutableSetOf<String>()
        val lower = text.lowercase()

        // 1. Financial / Receipt / Payments
        if (lower.contains("total") || lower.contains("subtotal") || lower.contains("tax") ||
            lower.contains("payment") || lower.contains("invoice") || lower.contains("order #") ||
            lower.contains("amount due") || lower.contains("receipt") || lower.contains("balance due") ||
            lower.contains("visa") || lower.contains("mastercard") || lower.contains("paid")) {
            tags.add("receipt")
            tags.add("finance")
        }

        // 2. Shopping / Orders / Delivery
        if (lower.contains("cart") || lower.contains("shipping") || lower.contains("delivery") ||
            lower.contains("tracking") || lower.contains("order confirmed") || lower.contains("checkout") ||
            lower.contains("amazon") || lower.contains("ebay") || lower.contains("promo") ||
            lower.contains("discount")) {
            tags.add("shopping")
            tags.add("order")
        }

        // 3. Code / Tech / Dev
        if (lower.contains("github") || lower.contains("git") || lower.contains("fun ") ||
            lower.contains("class ") || lower.contains("import ") || lower.contains("const ") ||
            lower.contains("return ") || lower.contains("stack overflow") || lower.contains("terminal") ||
            lower.contains("gradle") || lower.contains("npm ") || lower.contains("docker")) {
            tags.add("code")
            tags.add("tech")
        }

        // 4. Chat / Messaging
        if (lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("message") ||
            lower.contains("typing...") || lower.contains("slack") || lower.contains("discord") ||
            lower.contains("reply") || lower.contains("unread")) {
            tags.add("chat")
            tags.add("messaging")
        }

        // 5. Social / Post
        if (lower.contains("followers") || lower.contains("likes") || lower.contains("retweet") ||
            lower.contains("repost") || lower.contains("instagram") || lower.contains("twitter") ||
            lower.contains("x.com") || lower.contains("tiktok") || lower.contains("reddit")) {
            tags.add("social")
            tags.add("post")
        }

        // 6. Travel / Transit
        if (lower.contains("flight") || lower.contains("boarding pass") || lower.contains("gate") ||
            lower.contains("hotel") || lower.contains("airbnb") || lower.contains("terminal") ||
            lower.contains("uber") || lower.contains("lyft") || lower.contains("booking") ||
            lower.contains("depart") || lower.contains("arrive")) {
            tags.add("travel")
            tags.add("transit")
        }

        // 7. Security / Verification / 2FA
        if (lower.contains("verification code") || lower.contains("security code") ||
            lower.contains("one-time") || lower.contains("otp") || lower.contains("password") ||
            lower.contains("passcode") || lower.contains("2fa") || lower.contains("authentication")) {
            tags.add("verification")
            tags.add("security")
        }

        // 8. Event / Schedule
        if (lower.contains("meeting") || lower.contains("scheduled") || lower.contains("rsvp") ||
            lower.contains("zoom") || lower.contains("google meet") || lower.contains("calendar") ||
            lower.contains("reminder")) {
            tags.add("event")
            tags.add("schedule")
        }

        // 9. Brand & Platform entities
        val brands = listOf("github", "amazon", "youtube", "twitter", "reddit", "netflix", "spotify", "uber", "google", "apple")
        for (b in brands) {
            if (lower.contains(b)) tags.add(b)
        }

        return tags.toList().take(6)
    }

    /**
     * Extracts URLs from text.
     */
    fun extractDetectedLinks(text: String): List<String> {
        val regex = Regex("""(https?://[^\s<>"]+|www\.[^\s<>"]+)""")
        return regex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']') }
            .filter { it.length > 5 }
            .distinct()
            .toList()
    }

    /**
     * Extracts a concise title from the first descriptive line of text.
     */
    fun extractSalientTitle(text: String): String? {
        val firstLine = text.lines()
            .map { it.trim() }
            .firstOrNull { it.length in 4..60 && !it.startsWith("http") && it.any { c -> c.isLetter() } }
        return firstLine?.take(40)
    }
}
