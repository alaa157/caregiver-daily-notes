package com.caregiver.mobile.ai

/**
 * Shared handling of JSON replies from the AI providers: read the payload out
 * of whatever wrapper the model used, and spend exactly one repair retry when
 * the reply cannot be parsed.
 *
 * The repair retry is the one the README promises. The model is asked once
 * more, with its own rejected reply attached and an explicit instruction to
 * answer with json only. A reply that survives that retry is reported as
 * [LlmStatus.REPAIRED], so a caller can tell a repaired reply from a clean
 * one. There is no second repair attempt: a model that cannot produce json
 * after being told exactly what to do must not keep the user waiting.
 */
object LlmJson {
    /** Marker that opens the rejected reply in the repair attempt. */
    const val REPAIR_INSTRUCTION =
        "\n\n[FORMAT ERROR] The previous reply was not valid json for the requested schema.\n" +
            "Previous reply:\n"

    /** Instruction that closes the rejected reply in the repair attempt. */
    const val REPAIR_SUFFIX =
        "\n\nReply again with ONLY a single valid json object that matches the requested schema. " +
            "No fences, no commentary, no prose."

    /** How much of the rejected reply is shown back to the model. */
    const val MAX_REPAIR_CONTEXT_CHARS = 2000

    sealed class Reply<out T> {
        /**
         * [value] was parsed from the reply. [status] is [LlmStatus.REPAIRED]
         * when a repair retry produced it, [LlmStatus.OK] otherwise.
         */
        data class Parsed<T>(val value: T, val status: LlmStatus) : Reply<T>()

        /** The provider failed outright: there was no reply to parse. */
        data class Unavailable(val error: String?) : Reply<Nothing>()

        /** The provider replied, but no usable json could be read from it. */
        data class Malformed(val error: String?) : Reply<Nothing>()

        /** The reply was longer than the caller allows. */
        data class Oversized(val error: String?) : Reply<Nothing>()
    }

    /**
     * Runs [request] and reads its payload with [decode].
     *
     * [decode] receives the extracted json and returns null when it does not
     * match the expected shape; that is what triggers the single repair
     * retry. [maxChars] bounds the size of an accepted reply.
     */
    suspend fun <T> decode(
        client: LlmClient,
        request: LlmRequest,
        maxChars: Int,
        decode: (String) -> T?,
    ): Reply<T> {
        val first = client.complete(request)
        if (first.status == LlmStatus.FALLBACK) return Reply.Unavailable(first.error)
        if (first.text.length > maxChars) return Reply.Oversized(first.error)
        val direct = parse(first.text, decode)
        if (direct != null) return Reply.Parsed(direct, LlmStatus.OK)

        val repaired = client.complete(repairRequest(request, first.text))
        if (repaired.status == LlmStatus.FALLBACK) return Reply.Malformed(first.error)
        if (repaired.text.length > maxChars) return Reply.Oversized(repaired.error)
        val value = parse(repaired.text, decode) ?: return Reply.Malformed(first.error)
        return Reply.Parsed(value, LlmStatus.REPAIRED)
    }

    /** Extracts the json payload of [text] and hands it to [decode]. */
    private fun <T> parse(text: String, decode: (String) -> T?): T? {
        val candidate = ModelOutputSanitizer.extractJson(text) ?: return null
        return decode(candidate)
    }

    /**
     * The single repair attempt: the same request with the rejected reply and
     * an explicit format instruction appended to the data block.
     */
    private fun repairRequest(request: LlmRequest, rejected: String): LlmRequest =
        request.copy(
            dataBlock = request.dataBlock + REPAIR_INSTRUCTION +
                rejected.take(MAX_REPAIR_CONTEXT_CHARS) + REPAIR_SUFFIX,
        )
}
