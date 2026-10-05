package com.example.service.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class OnDeviceVisionMode {
    AUTOMATIC, FORCE_LOCAL, DISABLED;
    companion object {
        fun fromPreference(value: String): OnDeviceVisionMode = runCatching {
            valueOf(value.trim().uppercase(Locale.US).replace("-", "_").replace(" ", "_"))
        }.getOrDefault(AUTOMATIC)
    }
}

enum class DevicePerformanceTier { BASIC, STANDARD, POWERFUL, HIGH_END }
enum class OnDeviceVisionCapability { IMAGE_CAPTIONING, SCREENSHOT_ANALYSIS, OCR_CONTEXT, VISUAL_QA, TAG_SUGGESTIONS }

data class DeviceCapabilities(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val cpuCores: Int,
    val apiLevel: Int,
    val isLowRamDevice: Boolean,
    val performanceTier: DevicePerformanceTier
)

data class OnDeviceVisionModel(
    val id: String,
    val family: String,
    val displayName: String,
    val parameterCount: String,
    val quantization: String,
    val minRamMb: Int,
    val recommendedRamMb: Int,
    val storageMb: Int,
    val capabilities: Set<OnDeviceVisionCapability>,
    val notes: String = ""
)

data class OnDeviceModelRecommendation(
    val tier: DevicePerformanceTier,
    val recommended: OnDeviceVisionModel?,
    val alternatives: List<OnDeviceVisionModel>
)

data class OnDeviceVisionResult(
    val isSuccess: Boolean,
    val modelId: String,
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val modelUsed: String = "",
    val processingTimeMs: Long = 0L,
    val errorMessage: String? = null
)

object OnDeviceVisionCatalog {
    private val models = listOf(
        OnDeviceVisionModel("smolvlm-256m", "SmolVLM", "SmolVLM 256M", "256M", "4-bit", 1400, 2200, 450, setOf(OnDeviceVisionCapability.IMAGE_CAPTIONING, OnDeviceVisionCapability.SCREENSHOT_ANALYSIS, OnDeviceVisionCapability.OCR_CONTEXT, OnDeviceVisionCapability.TAG_SUGGESTIONS), "Smallest profile for memory-constrained phones."),
        OnDeviceVisionModel("smolvlm-500m", "SmolVLM", "SmolVLM 500M", "500M", "4-bit", 2200, 3200, 900, OnDeviceVisionCapability.entries.toSet(), "Balanced compact vision-language profile."),
        OnDeviceVisionModel("gemma-3n", "Gemma", "Gemma 3n Vision", "4B-class effective", "4-bit", 4200, 6000, 2800, OnDeviceVisionCapability.entries.toSet(), "Higher-quality local analysis on capable devices."),
        OnDeviceVisionModel("qwen2.5-vl-3b", "Qwen-VL", "Qwen2.5-VL 3B", "3B", "4-bit", 5200, 7000, 3200, OnDeviceVisionCapability.entries.toSet(), "Strong visual QA and structured screenshot understanding."),
        OnDeviceVisionModel("minicpm-v-2.6", "MiniCPM-V", "MiniCPM-V 2.6", "8B-class", "4-bit", 7600, 10000, 5200, OnDeviceVisionCapability.entries.toSet(), "High-end profile with a larger memory footprint.")
    )

    fun all(): List<OnDeviceVisionModel> = models

    fun recommend(capabilities: DeviceCapabilities): OnDeviceModelRecommendation {
        val supported = models.filter { it.minRamMb <= capabilities.totalRamMb }
        if (supported.isEmpty()) return OnDeviceModelRecommendation(capabilities.performanceTier, null, emptyList())
        val recommended = when (capabilities.performanceTier) {
            DevicePerformanceTier.BASIC -> supported.minByOrNull { it.minRamMb }
            DevicePerformanceTier.STANDARD -> supported.minByOrNull { kotlin.math.abs(it.recommendedRamMb - capabilities.totalRamMb) }
            DevicePerformanceTier.POWERFUL, DevicePerformanceTier.HIGH_END -> supported.maxByOrNull { it.minRamMb }
        }
        return OnDeviceModelRecommendation(capabilities.performanceTier, recommended, supported.filter { it.id != recommended?.id }.sortedBy { kotlin.math.abs(it.recommendedRamMb - capabilities.totalRamMb) })
    }
}

object OnDeviceVisionCapabilityDetector {
    fun detect(context: Context): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024L * 1024L)
        val availableRamMb = memoryInfo.availMem / (1024L * 1024L)
        val cores = Runtime.getRuntime().availableProcessors()
        val lowRam = activityManager?.isLowRamDevice ?: false
        val tier = when {
            totalRamMb >= 12000 && cores >= 8 -> DevicePerformanceTier.HIGH_END
            totalRamMb >= 7000 && cores >= 6 -> DevicePerformanceTier.POWERFUL
            totalRamMb >= 4000 && cores >= 4 -> DevicePerformanceTier.STANDARD
            else -> DevicePerformanceTier.BASIC
        }
        return DeviceCapabilities(totalRamMb, availableRamMb, cores, Build.VERSION.SDK_INT, lowRam, tier)
    }
}

interface OnDeviceVisionEngine {
    val modelId: String
    suspend fun isAvailable(): Boolean
    suspend fun analyze(imagePath: String, qualityPreset: String = "Balanced"): OnDeviceVisionResult
    suspend fun close()
}

class OnDeviceVisionService(
    private val context: Context,
    private val engineFactory: (OnDeviceVisionModel) -> OnDeviceVisionEngine? = { null }
) {
    suspend fun installedModels(): List<OnDeviceVisionModel> = withContext(Dispatchers.Default) {
        OnDeviceVisionCatalog.all().filter { model ->
            val engine = engineFactory(model) ?: return@filter false
            try {
                engine.isAvailable()
            } finally {
                engine.close()
            }
        }
    }

    suspend fun recommend(): OnDeviceModelRecommendation = withContext(Dispatchers.Default) {
        OnDeviceVisionCatalog.recommend(OnDeviceVisionCapabilityDetector.detect(context))
    }

    suspend fun recommendationForInstalledModels(): OnDeviceModelRecommendation {
        val deviceRecommendation = recommend()
        val installedIds = installedModels().map { it.id }.toSet()
        val candidates = listOfNotNull(deviceRecommendation.recommended) + deviceRecommendation.alternatives
        val available = candidates.filter { installedIds.contains(it.id) }
        return OnDeviceModelRecommendation(
            tier = deviceRecommendation.tier,
            recommended = available.firstOrNull(),
            alternatives = available.drop(1)
        )
    }

    suspend fun resolveModel(preference: String): OnDeviceVisionModel? {
        val capabilities = OnDeviceVisionCapabilityDetector.detect(context)
        return if (preference.isBlank() || preference == "auto") {
            OnDeviceVisionCatalog.recommend(capabilities).recommended
        } else {
            OnDeviceVisionCatalog.all().firstOrNull {
                it.id == preference && it.minRamMb <= capabilities.totalRamMb
            }
        }
    }

    suspend fun analyze(
        imagePath: String,
        mode: OnDeviceVisionMode,
        modelPreference: String,
        qualityPreset: String
    ): OnDeviceVisionResult {
        if (mode == OnDeviceVisionMode.DISABLED) {
            return OnDeviceVisionResult(
                isSuccess = false,
                modelId = modelPreference,
                errorMessage = "On-device vision is disabled."
            )
        }

        val model = resolveModel(modelPreference)
            ?: return OnDeviceVisionResult(
                isSuccess = false,
                modelId = modelPreference,
                errorMessage = "No compatible on-device vision model is available for this device."
            )

        val engine = engineFactory(model)
            ?: return OnDeviceVisionResult(
                isSuccess = false,
                modelId = model.id,
                errorMessage = "Model runtime is not installed for the selected local model."
            )

        return try {
            if (!engine.isAvailable()) {
                OnDeviceVisionResult(
                    isSuccess = false,
                    modelId = model.id,
                    errorMessage = "Model runtime is unavailable."
                )
            } else {
                val started = SystemClock.elapsedRealtime()
                engine.analyze(imagePath, qualityPreset).copy(
                    modelId = model.id,
                    modelUsed = model.displayName,
                    processingTimeMs = SystemClock.elapsedRealtime() - started
                )
            }
        } finally {
            engine.close()
        }
    }
}