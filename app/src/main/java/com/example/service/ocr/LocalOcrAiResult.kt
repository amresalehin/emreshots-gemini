package com.amresalehin.emreshots.service.ocr

/**
 * Result from local text-only AI processing of OCR text (PixelShot style).
 * Includes repaired OCR transcript, semantic categorization tags, links, and title.
 */
data class LocalOcrAiResult(
    val fixedOcrText: String,
    val tags: List<String> = emptyList(),
    val title: String? = null,
    val detectedLinks: List<String> = emptyList()
)
