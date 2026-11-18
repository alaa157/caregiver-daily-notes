package com.caregiver.mobile.ai

import com.caregiver.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AiDataUseNoticeTest {

    @Test
    fun keyTierNoticeMapsToGeminiString() {
        assertEquals(R.string.ai_data_use_gemini, AiDataUseNotice.KEY_TIER.stringRes())
    }

    @Test
    fun freeHostNoticeMapsToFreeHostString() {
        assertEquals(R.string.ai_data_use_free_host, AiDataUseNotice.FREE_HOST.stringRes())
    }
}
