package com.caregiver.mobile.ai

/**
 * One model the app offers for a provider.
 */
data class AiModelOption(
    val id: String,
    /**
     * True when the model can be used without paying: it sits in the
     * provider's free tier, or it is a free variant.
     */
    val free: Boolean = false,
)

/**
 * The models the app suggests per provider.
 *
 * The model field stays free text — a provider can serve models this list
 * has never heard of — but the suggestions exist so a typo in a model id shows
 * up in the settings instead of much later as an opaque failure.
 *
 * The list is dated knowledge: providers retire models (gpt-4o-mini and
 * gemini-2.0-flash are gone, grok-3 was retired in May 2026), which is why
 * the provider defaults point at the same ids.
 */
object AiModelCatalog {
    fun options(provider: AiProvider): List<AiModelOption> = when (provider) {
        AiProvider.OPENAI -> listOf(
            AiModelOption("gpt-6-luna"),
            AiModelOption("gpt-5.4-mini"),
            AiModelOption("gpt-6.1-sol"),
            AiModelOption("gpt-6-sol"),
        )

        AiProvider.GEMINI -> listOf(
            AiModelOption("gemini-3.8-flash"),
            AiModelOption("gemini-3.5-flash"),
            AiModelOption("gemini-3.5-flash-lite"),
            // The one Gemini model the free tier still covers.
            AiModelOption("gemini-3.1-flash-lite", free = true),
        )

        AiProvider.ANTHROPIC -> listOf(
            AiModelOption("claude-sonnet-5-5"),
            AiModelOption("claude-haiku-5-5"),
            AiModelOption("claude-opus-5-5"),
            AiModelOption("claude-fable-5-1"),
        )

        AiProvider.XAI -> listOf(
            AiModelOption("grok-4.7"),
            AiModelOption("grok-4.5"),
            AiModelOption("grok-4.3"),
            AiModelOption("grok-4.20"),
        )

        AiProvider.OPEN_ROUTER -> listOf(
            // The router picks a free model that fits the request.
            AiModelOption("openrouter/free", free = true),
            AiModelOption("openai/gpt-4o-mini"),
            // Free variants come and go; treat them as a promotion.
            AiModelOption("nvidia/nemotron-3.5-lightning:free", free = true),
            AiModelOption("google/gemma-4-31b-it:free", free = true),
        )
    }
}
