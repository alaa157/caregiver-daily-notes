package com.caregiver.mobile.ai

import kotlinx.serialization.json.Json

/**
 * Reads the JSON payload out of raw model output.
 *
 * Models rarely answer with bare JSON: they wrap it in a ```json fence, add a
 * sentence before or after it, or emit a `<think>` block first. Parsing the raw
 * text then fails and a perfectly good reply is thrown away as malformed. Both
 * AI services read their payload through this helper, so one set of rules
 * decides what counts as a JSON reply.
 *
 * The helpers are pure and never interpret the content: text injected into a
 * JSON string is left untouched for the callers' own validation to reject.
 */
object ModelOutputSanitizer {
    private val json = Json { ignoreUnknownKeys = true }

    /** Chain-of-thought blocks some models emit before the answer. */
    private val thinkBlock = Regex("(?is)<think>.*?</think>")

    /** A fence marker on its own line, with or without a language tag. */
    private val fenceLine = Regex(
        "^[ \\t]*```[A-Za-z0-9_+.-]*[ \\t]*$",
        RegexOption.MULTILINE,
    )

    /**
     * Removes reasoning blocks and code fences, then trims. Text without any
     * wrapper is returned unchanged (apart from trimming).
     */
    fun strip(raw: String): String {
        if (raw.isBlank()) return ""
        val withoutThink = thinkBlock.replace(raw, " ")
        return fenceLine.replace(withoutThink, "").trim()
    }

    /**
     * Returns the JSON object or array carried by [raw], or null when the
     * reply holds none. Wrappers are removed first and the outermost balanced
     * value is located afterwards, so a preamble or trailing prose cannot
     * break the extraction.
     *
     * When the reply carries several candidates, the largest one wins: prose
     * that mentions braces ("here is the {summary}") must not shadow the
     * payload that follows it.
     */
    fun extractJson(raw: String): String? {
        val cleaned = strip(raw)
        if (cleaned.isEmpty()) return null
        return jsonCandidates(cleaned)
            .filter { isJsonStructure(it) }
            .maxByOrNull { it.length }
    }

    /**
     * True when [text] holds a single JSON object or array.
     *
     * The json parser itself is lenient about bare literals ("nope" parses as
     * a literal), so the shape is checked here: prose is not a json reply.
     */
    fun isJsonStructure(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return false
        if (!trimmed.endsWith("}") && !trimmed.endsWith("]")) return false
        return runCatching { json.parseToJsonElement(trimmed) }.isSuccess
    }

    /** Balanced `{...}` / `[...]` candidates found in [text], outermost first. */
    private fun jsonCandidates(text: String): List<String> {
        val candidates = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '{' || char == '[') {
                val end = matchingEnd(text, index)
                if (end > index) {
                    candidates += text.substring(index, end + 1)
                    index = end + 1
                    continue
                }
            }
            index++
        }
        return candidates
    }

    /**
     * Index of the bracket closing the one at [start], or -1 when the value is
     * unbalanced. Braces inside strings are not counted.
     */
    private fun matchingEnd(text: String, start: Int): Int {
        val open = mutableListOf<Char>()
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{', '[' -> open += char
                '}', ']' -> {
                    val expected = open.removeLastOrNull() ?: return -1
                    val closing = if (expected == '{') '}' else ']'
                    if (char != closing) return -1
                    if (open.isEmpty()) return index
                }
            }
        }
        return -1
    }
}
