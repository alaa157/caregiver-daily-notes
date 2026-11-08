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
    /**
     * The provider's chat endpoint accepts a JSON-mode constraint
     * (`response_format` / `responseMimeType`) for this model. The client
     * detects non-support at runtime and retries without it, but a model
     * known to support it is preferred for the grounded-summary task.
     */
    val jsonMode: Boolean = true,
    /** The provider's known compatibility note, when there is one. */
    val note: String? = null,
    /**
     * True for the model the app recommends for summaries: a verified
     * JSON-mode model with faithful instruction following. A free model is
     * only recommended when it has proven reliable on the grounded task.
     */
    val recommendedSummary: Boolean = false,
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
    /**
     * The model recommended for the grounded-summary task on [provider]:
     * a verified JSON-mode model with faithful instruction following, so
     * verbatim-quote grounding succeeds. A paid default is recommended over
     * a free variant unless the free variant has proven reliable here.
     */
    fun recommendedSummaryModel(provider: AiProvider): AiModelOption? =
        options(provider).firstOrNull { it.recommendedSummary }
            ?: options(provider).firstOrNull { it.jsonMode }
            ?: options(provider).firstOrNull()

    fun options(provider: AiProvider): List<AiModelOption> = when (provider) {
        AiProvider.OPENAI -> listOf(
            AiModelOption("gpt-6-luna", recommendedSummary = true),
            AiModelOption("gpt-5.4-mini"),
            AiModelOption("gpt-6.1-sol"),
            AiModelOption("gpt-6-sol"),
        )

        AiProvider.GEMINI -> listOf(
            // Paid first: the free tier may train on care notes.
            AiModelOption("gemini-3.8-flash", recommendedSummary = true),
            AiModelOption("gemini-3.5-flash"),
            AiModelOption("gemini-3.5-flash-lite"),
            // The one Gemini model the free tier still covers. Opt-in.
            AiModelOption("gemini-3.1-flash-lite", free = true),
        )

        AiProvider.ANTHROPIC -> listOf(
            AiModelOption("claude-sonnet-5-5", recommendedSummary = true),
            AiModelOption("claude-haiku-5-5"),
            AiModelOption("claude-opus-5-5"),
            AiModelOption("claude-fable-5-1"),
        )

        AiProvider.XAI -> listOf(
            AiModelOption("grok-4.7", recommendedSummary = true),
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
            // Proven on the grounded-summary task (verbatim quotes survive
            // validation); the recommended free choice for OpenRouter.
            AiModelOption(
                "google/gemma-4-31b-it:free",
                free = true,
                recommendedSummary = true,
            ),
        )
    }
}
