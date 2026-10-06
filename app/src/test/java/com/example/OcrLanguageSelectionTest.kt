package com.amresalehin.emreshots

import com.amresalehin.emreshots.data.local.AppPreferences
import com.amresalehin.emreshots.service.ocr.LocalOcrService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLanguageSelectionTest {
    @Test
    fun exposesTopTenLanguagesByName() {
        assertEquals(
            listOf("English", "Chinese", "Hindi", "Spanish", "French", "Arabic", "Bengali", "Portuguese", "Indonesian", "Urdu"),
            LocalOcrService.supportedLanguages.map { it.name }
        )
    }

    @Test
    fun legacyScriptPreferencesMigrateToLanguageNames() {
        assertEquals("English", AppPreferences.normalizeOcrLanguage("Latin"))
        assertEquals("Hindi", AppPreferences.normalizeOcrLanguage("Devanagari"))
        assertEquals("Chinese", AppPreferences.normalizeOcrLanguage("chi_sim"))
        assertEquals("Bengali", AppPreferences.normalizeOcrLanguage("ben"))
        assertEquals("Arabic", AppPreferences.normalizeOcrLanguage("Arabic"))
        assertTrue(AppPreferences.normalizeOcrLanguage("not-a-language") == null)
    }
}
