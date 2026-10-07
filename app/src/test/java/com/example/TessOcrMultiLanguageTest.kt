package com.amresalehin.emreshots

import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.service.ocr.TessDataManager
import com.amresalehin.emreshots.service.ocr.TessLanguage
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TessOcrMultiLanguageTest {

    private lateinit var viewModel: ScreenshotsViewModel
    private lateinit var dataManager: TessDataManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = ScreenshotsViewModel(app)
        dataManager = TessDataManager(app)
    }

    @Test
    fun testTessLanguageCatalogContainsMajorGlobalLanguages() {
        val languages = TessLanguage.ALL_LANGUAGES
        assertTrue("Catalog should contain at least 25 languages", languages.size >= 25)

        // Check key language coverage
        val eng = TessLanguage.findByCode("eng")
        assertEquals("English", eng.englishName)
        assertTrue(eng.isBundled)

        val spa = TessLanguage.findByCode("spa")
        assertEquals("Spanish", spa.englishName)
        assertEquals("Español", spa.nativeName)

        val deu = TessLanguage.findByCode("deu")
        assertEquals("German", deu.englishName)

        val tur = TessLanguage.findByCode("tur")
        assertEquals("Turkish", tur.englishName)
        assertEquals("Türkçe", tur.nativeName)

        val fra = TessLanguage.findByCode("fra")
        assertEquals("French", fra.englishName)

        val chi = TessLanguage.findByCode("chi_sim")
        assertEquals("Chinese (Simplified)", chi.englishName)

        val jpn = TessLanguage.findByCode("jpn")
        assertEquals("Japanese", jpn.englishName)

        val ara = TessLanguage.findByCode("ara")
        assertEquals("Arabic", ara.englishName)

        val hin = TessLanguage.findByCode("hin")
        assertEquals("Hindi", hin.englishName)

        val rus = TessLanguage.findByCode("rus")
        assertEquals("Russian", rus.englishName)
    }

    @Test
    fun testTessLanguageDownloadUrls() {
        val tur = TessLanguage.findByCode("tur")
        assertTrue(tur.downloadUrl.contains("tessdata_fast/main/tur.traineddata"))

        val spa = TessLanguage.findByCode("spa")
        assertTrue(spa.downloadUrl.contains("tessdata_fast/main/spa.traineddata"))
    }

    @Test
    fun testUnknownLanguageCodeFallback() {
        val custom = TessLanguage.findByCode("xyz")
        assertNotNull(custom)
        assertEquals("XYZ", custom.englishName)
        assertEquals("xyz", custom.code)
    }

    @Test
    fun testTessDataManagerPaths() {
        assertNotNull(dataManager.tessDataDir)
        assertTrue(dataManager.tessDataDir.name == "tessdata")
        assertNotNull(dataManager.dataPath)
    }

    @Test
    fun testOcrEngineAndLanguageViewModelControls() {
        assertEquals("tesseract", viewModel.ocrEngine.value)
        assertEquals("eng", viewModel.ocrLanguage.value)

        // Change engine to ML Kit
        viewModel.setOcrEngine("mlkit")
        assertEquals("mlkit", viewModel.ocrEngine.value)

        // Switch back to Tesseract
        viewModel.setOcrEngine("tesseract")
        assertEquals("tesseract", viewModel.ocrEngine.value)

        // Change language to Turkish
        viewModel.setOcrLanguage("tur")
        assertEquals("tur", viewModel.ocrLanguage.value)

        // Change language to Spanish
        viewModel.setOcrLanguage("spa")
        assertEquals("spa", viewModel.ocrLanguage.value)
    }
}
