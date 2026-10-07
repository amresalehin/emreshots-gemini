package com.amresalehin.emreshots

import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emreshots.service.ocr.OcrArtefactLlmFixer
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrArtefactLlmFixerTest {

    private lateinit var fixer: OcrArtefactLlmFixer
    private lateinit var viewModel: ScreenshotsViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        fixer = OcrArtefactLlmFixer(app)
        viewModel = ScreenshotsViewModel(app)
    }

    @Test
    fun testRuleBasedHyphenationFix() {
        val broken = "This is an infor-\nmation system that can rec-\nognize text."
        val cleaned = fixer.cleanupOcrArtefactsRuleBased(broken)
        assertTrue(cleaned.contains("information"))
        assertTrue(cleaned.contains("recognize"))
        assertFalse(cleaned.contains("infor-"))
        assertFalse(cleaned.contains("rec-"))
    }

    @Test
    fun testRuleBasedUrlProtocolFix() {
        val broken = "Check out https : / / github.com and www . example.com"
        val cleaned = fixer.cleanupOcrArtefactsRuleBased(broken)
        assertTrue(cleaned.contains("https://github.com"))
        assertTrue(cleaned.contains("www.example.com"))
    }

    @Test
    fun testRuleBasedPunctuationAndContractionFix() {
        val broken = "Hello , world ! Don ' t worry , it ' s fine ."
        val cleaned = fixer.cleanupOcrArtefactsRuleBased(broken)
        assertTrue(cleaned.contains("Hello, world!"))
        assertTrue(cleaned.contains("Don't worry, it's fine."))
    }

    @Test
    fun testRuleBasedNoiseLinesFilter() {
        val withNoise = """
            Welcome to the App
            ~
            |
            User account details
            ---
            Logged in successfully
        """.trimIndent()
        val cleaned = fixer.cleanupOcrArtefactsRuleBased(withNoise)
        assertTrue(cleaned.contains("Welcome to the App"))
        assertTrue(cleaned.contains("User account details"))
        assertTrue(cleaned.contains("Logged in successfully"))
        // Stray noise lines should be eliminated
        val lines = cleaned.lines().map { it.trim() }
        assertFalse(lines.contains("~"))
        assertFalse(lines.contains("|"))
    }

    @Test
    fun testViewModelFixArtefactsPreferenceToggle() {
        assertTrue(viewModel.fixOcrArtefactsEnabled.value)
        viewModel.setFixOcrArtefactsEnabled(false)
        assertFalse(viewModel.fixOcrArtefactsEnabled.value)
        viewModel.setFixOcrArtefactsEnabled(true)
        assertTrue(viewModel.fixOcrArtefactsEnabled.value)
    }

    @Test
    fun testPixelShotSemanticTaggingReceipt() {
        val ocrReceipt = """
            Walmart Supercenter
            Order # 98412
            Subtotal: $45.20
            Tax: $3.80
            Total Amount Due: $49.00
            Paid with Visa
        """.trimIndent()
        val tags = fixer.extractSemanticTags(ocrReceipt)
        assertTrue(tags.contains("receipt"))
        assertTrue(tags.contains("finance"))
    }

    @Test
    fun testPixelShotSemanticTaggingCode() {
        val ocrCode = """
            import kotlinx.coroutines.*
            // github.com repo
            fun calculateMetrics(): Int {
                return 42
            }
        """.trimIndent()
        val tags = fixer.extractSemanticTags(ocrCode)
        assertTrue(tags.contains("code"))
        assertTrue(tags.contains("tech"))
        assertTrue(tags.contains("github"))
    }

    @Test
    fun testDirectProcessOcrWithLocalAi() = kotlinx.coroutines.runBlocking {
        val raw = "Order # 12345 total : $ 99.99 con- \n firmation https : / / amazon . com / order"
        val result = fixer.processOcrWithLocalAi(raw)
        assertTrue(result.fixedOcrText.contains("confirmation"))
        assertTrue(result.fixedOcrText.contains("https://amazon.com/order"))
        assertTrue(result.tags.isNotEmpty())
        assertTrue(result.tags.contains("receipt") || result.tags.contains("finance") || result.tags.contains("shopping"))
        assertTrue(result.detectedLinks.contains("https://amazon.com/order"))
    }
}
