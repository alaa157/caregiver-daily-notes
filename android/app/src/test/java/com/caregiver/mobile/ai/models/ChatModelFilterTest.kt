package com.caregiver.mobile.ai.models

import com.caregiver.mobile.ai.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chat-capability filter every model-list fetcher applies to its results. */
class ChatModelFilterTest {

    @Test
    fun openAiEmbeddingIsNotAChatModel() {
        assertFalse(isChatModel(AiProvider.OPENAI, "text-embedding-3-large"))
    }

    @Test
    fun openAiAudioAndImageModelsAreNotChatModels() {
        assertFalse(isChatModel(AiProvider.OPENAI, "whisper-1"))
        assertFalse(isChatModel(AiProvider.OPENAI, "tts-1"))
        assertFalse(isChatModel(AiProvider.OPENAI, "dall-e-3"))
    }

    @Test
    fun xAiImageModelIsNotAChatModel() {
        assertFalse(isChatModel(AiProvider.XAI, "grok-2-image"))
    }

    @Test
    fun openRouterModelsAreChatModels() {
        assertTrue(isChatModel(AiProvider.OPEN_ROUTER, "openai/gpt-4o-mini"))
        assertTrue(isChatModel(AiProvider.OPEN_ROUTER, "nvidia/x:free"))
    }

    @Test
    fun anthropicModelsAreAlwaysChatModels() {
        for (id in listOf("claude-sonnet-5-5", "claude-embedding", "anything")) {
            assertTrue(isChatModel(AiProvider.ANTHROPIC, id))
        }
    }

    @Test
    fun geminiModelsAreAlwaysChatModels() {
        // Gemini capability comes from supportedGenerationMethods, which its own source checks.
        for (id in listOf("gemini-3.8-flash", "gemini-embedding", "anything")) {
            assertTrue(isChatModel(AiProvider.GEMINI, id))
        }
    }

    @Test
    fun filterIsCaseInsensitive() {
        assertFalse(isChatModel(AiProvider.OPENAI, "TEXT-EMBEDDING-3-LARGE"))
        assertFalse(isChatModel(AiProvider.XAI, "Grok-2-Image"))
    }

    @Test
    fun otherNonChatModelsAreFilteredOut() {
        for (id in listOf(
            "text-moderation-latest", "gpt-4o-realtime-preview", "gpt-4o-audio-preview",
            "davinci", "babbage-002", "whisper-transcribe",
        )) {
            assertFalse(isChatModel(AiProvider.OPENAI, id))
        }
    }

    @Test
    fun regularChatModelsAreKept() {
        assertTrue(isChatModel(AiProvider.OPENAI, "gpt-6-luna"))
        assertTrue(isChatModel(AiProvider.XAI, "grok-4.7"))
    }

    @Test
    fun normalizeBaseUrlRemovesTheTrailingSlash() {
        assertEquals("https://api.x.ai/v1", normalizeBaseUrl("https://api.x.ai/v1/"))
    }

    @Test
    fun normalizeBaseUrlOfBlankIsEmpty() {
        assertEquals("", normalizeBaseUrl(""))
    }
}
