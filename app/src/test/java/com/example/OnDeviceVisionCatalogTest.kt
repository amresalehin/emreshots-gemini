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
        assertTrue(families.contains("Qwen-VL"))
        assertTrue(families.contains("MiniCPM-V"))
    }

    @Test
    fun basicDeviceGetsSmallModel() {
        val caps = DeviceCapabilities(2048, 900, 4, 35, true, DevicePerformanceTier.BASIC)
        val result = OnDeviceVisionCatalog.recommend(caps)
        assertEquals("smolvlm-256m", result.recommended?.id)
    }

    @Test
    fun highEndDeviceGetsLargestCompatibleModel() {
        val caps = DeviceCapabilities(16384, 10000, 12, 35, false, DevicePerformanceTier.HIGH_END)
        val result = OnDeviceVisionCatalog.recommend(caps)
        assertEquals("minicpm-v-2.6", result.recommended?.id)
        assertTrue(result.alternatives.none { it.id == result.recommended?.id })
    }

    @Test
    fun insufficientRamReturnsNoRecommendation() {
        val caps = DeviceCapabilities(1024, 600, 4, 35, true, DevicePerformanceTier.BASIC)
        val result = OnDeviceVisionCatalog.recommend(caps)
        assertEquals(null, result.recommended)
    }
}
