package com.amresalehin.emreshots.service.ai

data class AiAnalysisResult(
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val detectedLinks: List<String> = emptyList(),
    val suggestedCollection: String? = null,
    val exifUserComment: String? = null,
    val ocrText: String? = null,
    val modelUsed: String = "",
    val processingTimeMs: Long = 0L,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val latencyMs: Long = 0L,
    val statusCode: Int = 0,
    val message: String,
    val availableModels: List<String> = emptyList()
)

data class FetchModelsResult(
    val isSuccess: Boolean,
    val models: List<String> = emptyList(),
    val message: String = "",
    val suggestedModels: List<String> = emptyList(),
    val latencyMs: Long = 0L
)
