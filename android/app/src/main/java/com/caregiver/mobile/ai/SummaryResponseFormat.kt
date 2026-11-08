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
 * present and every observation must carry a claim/text, noteId and
 * quote/evidence string. Optional fields follow the production decoder's
 * defaults. Connection testing stays separate and only proves reachability.
 */
object SummaryResponseFormat {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null when [text] matches the expected shape, else a short reason. */
    fun validate(text: String): String? {
        // The provider may wrap the payload in a fence or a sentence; the
        // shared reader extracts the json before the shape is checked.
        val payload = ModelOutputSanitizer.extractJson(text) ?: return "not a JSON object"
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return "not a JSON object"
        val observations = root["observations"] as? JsonArray ?: return "missing observations array"
        val uncertainties = root["uncertainties"] ?: return "missing uncertainties array"
        if (uncertainties !is JsonArray) return "uncertainties is not an array"
        observations.forEachIndexed { index, element ->
            val observation = element as? JsonObject ?: return "observation $index is not an object"
            if ((observation["noteId"] as? JsonPrimitive)?.isString != true) return "observation $index has no noteId"
            if ((observation["text"] as? JsonPrimitive)?.isString != true &&
                (observation["claim"] as? JsonPrimitive)?.isString != true) return "observation $index has no text"
            val evidenceType = (observation["evidenceType"] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim()?.uppercase()
            when (evidenceType) {
                null, "" -> {
                    if ((observation["quote"] as? JsonPrimitive)?.isString != true &&
                        (observation["evidence"] as? JsonPrimitive)?.isString != true) return "observation $index has no quote"
                }
                "STRUCTURED_FIELD" -> {
                    // A structured citation carries no verbatim quote; the
                    // field and value replace it.
                    if (((observation["structuredField"] as? JsonPrimitive)?.isString != true) ||
                        (observation["structuredField"] as? JsonPrimitive)?.content?.isBlank() == true) {
                        return "observation $index has no structuredField"
                    }
                    val value = observation["structuredValue"] as? JsonPrimitive
                    if (value == null || value.content.isBlank()) return "observation $index has no structuredValue"
                }
                "NOTE_TEXT", "ADDENDUM" -> {
                    if ((observation["quote"] as? JsonPrimitive)?.isString != true &&
                        (observation["evidence"] as? JsonPrimitive)?.isString != true) return "observation $index has no quote"
                }
                else -> return "observation $index has unknown evidenceType"
            }
        }
        return null
    }
}
