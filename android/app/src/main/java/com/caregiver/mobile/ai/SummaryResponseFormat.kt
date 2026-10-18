package com.caregiver.mobile.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Shape check for summary JSON replies, used by the settings screen's
 * feature-level format validation. Deliberately strict (mirroring the
 * backend parser and the summary prompt): both top-level arrays must be
 * present and every observation must carry string `noteId`, `quote` and
 * `text`. Connection testing stays separate and only proves reachability.
 */
object SummaryResponseFormat {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null when [text] matches the expected shape, else a short reason. */
    fun validate(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text.trim()).jsonObject }.getOrNull()
            ?: return "not a JSON object"
        val observations = root["observations"] as? JsonArray ?: return "missing observations array"
        root["uncertainties"] as? JsonArray ?: return "missing uncertainties array"
        observations.forEachIndexed { index, element ->
            val observation = element as? JsonObject ?: return "observation $index is not an object"
            if ((observation["noteId"] as? JsonPrimitive)?.isString != true) return "observation $index has no noteId"
            if ((observation["quote"] as? JsonPrimitive)?.isString != true) return "observation $index has no quote"
            if ((observation["text"] as? JsonPrimitive)?.isString != true) return "observation $index has no text"
        }
        return null
    }
}
