package com.amresalehin.emreshots

import com.amresalehin.emreshots.service.ai.DeviceCapabilities
import com.amresalehin.emreshots.service.ai.DevicePerformanceTier
import com.amresalehin.emreshots.service.ai.OnDeviceVisionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceVisionCatalogTest {
    @Test
    fun catalogContainsMultipleVisionFamilies() {
        val families = OnDeviceVisionCatalog.all().map { it.family }.toSet()
        assertTrue(families.contains("SmolVLM"))
        assertTrue(families.contains("Gemma"))
        assertTrue(families.contains("Qwen2.5-VL"))
        assertTrue(families.contains("MiniCPM-V"))
    }

    @Test
    fun basicDeviceGetsSmallModel() {
        val caps = DeviceCapabilities(totalRamMb=4096, availableRamMb=900, totalStorageMb=64000, availableStorageMb=20000, cpuCores=4, apiLevel=35, abi="arm64-v8a", isLowRamDevice=true, hasGpu=true, hasVulkan=true, performanceTier=DevicePerformanceTier.BASIC)
        val result = OnDeviceVisionCatalog.recommend(caps)
        assertEquals("smolvlm-256m-q4", result.recommended?.id)
    }

    @Test
    fun highEndDeviceGetsLargestCompatibleModel() {
        val caps = DeviceCapabilities(totalRamMb=16384, availableRamMb=10000, totalStorageMb=128000, availableStorageMb=64000, cpuCores=12, apiLevel=35, abi="arm64-v8a", isLowRamDevice=false, hasGpu=true, hasVulkan=true, performanceTier=DevicePerformanceTier.HIGH_END)
        val result = OnDeviceVisionCatalog.recommend(caps)
        assertTrue(result.recommended != null)
        assertTrue(result.alternatives.none { it.id == result.recommended?.id })
    }

    @Test
    fun insufficientRamReturnsNoRecommendation() {
        val caps = DeviceCapabilities(totalRamMb=1024, availableRamMb=600, totalStorageMb=64000, availableStorageMb=20000, cpuCores=4, apiLevel=35, abi="arm64-v8a", isLowRamDevice=true, hasGpu=true, hasVulkan=true, performanceTier=DevicePerformanceTier.BASIC)
        val result = OnDeviceVisionCatalog.recommend(caps)
        assertEquals(null, result.recommended)
    }
    @Test
    fun smolVlmDownloadsArePinned() {
        val q4 = OnDeviceVisionCatalog.find("smolvlm-256m-q4")!!
        assertEquals(125053120L, q4.artifacts.first { it.id == "base" }.sizeBytes)
        assertTrue(q4.artifacts.all { it.sha256.length == 64 })
        assertTrue(q4.artifacts.all { it.url.contains("ggml-org/SmolVLM-256M-Instruct-GGUF") })

        val q8 = OnDeviceVisionCatalog.find("smolvlm-256m-q8")!!
        assertTrue(q8.artifacts.isNotEmpty())
        assertTrue(q8.artifacts.all { it.sha256.length == 64 })
    }

}
