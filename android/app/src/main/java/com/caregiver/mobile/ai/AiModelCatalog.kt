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
 * has never heard of, and the caregiver can always type their own id — but
 * the suggestions exist so a typo in a model id shows up in the settings
 * instead of much later as an opaque failure.
 *
 * The list is dated knowledge: direct provider defaults follow current model
 * ids (gpt-4o-mini and gemini-2.0-flash were retired or deprecated, as was
 * grok-3 in May 2026). OpenRouter's separate route to openai/gpt-4o-mini
 * remains available and is listed under that provider.
 *
 * Paid models lead, and a provider's default is a paid model. On Gemini's
 * free tier the notes may be used to improve Google's products, and an
 * OpenRouter free model keeps whatever data policy its host has — some of
 * them train on what they receive. A free model is therefore an explicit
 * opt-in, never the first suggestion.
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
            // Paid first: the free tier may train on care notes.
            AiModelOption("gemini-3.8-flash"),
            AiModelOption("gemini-3.5-flash"),
            AiModelOption("gemini-3.5-flash-lite"),
            // The one Gemini model the free tier still covers. Opt-in.
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
            AiModelOption("openai/gpt-4o-mini"),
            // Free variants come and go, and may train on what they receive;
            // treat them as a deliberate promotion, not a default.
            AiModelOption("openrouter/free", free = true),
            AiModelOption("nvidia/nemotron-3.5-lightning:free", free = true),
            AiModelOption("google/gemma-4-31b-it:free", free = true),
        )
    }
}
