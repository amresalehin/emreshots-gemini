package com.amresalehin.emreshots

import com.amresalehin.emreshots.viewmodel.AiOcrModelOption
import org.junit.Assert.assertEquals
import org.junit.Test

class AiOcrModelOptionTest {
    @Test
    fun modelChoiceKeepsProviderAndModelIdentity() {
        val option = AiOcrModelOption(
            providerId = "provider-1",
            providerName = "Cloud AI",
            modelName = "gemini-2.5-flash"
        )

        assertEquals("provider-1", option.providerId)
        assertEquals("Cloud AI", option.providerName)
        assertEquals("gemini-2.5-flash", option.modelName)
    }
}
