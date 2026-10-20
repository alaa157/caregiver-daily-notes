package com.caregiver.mobile.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One note as it is shown to the model. */
@Serializable
internal data class PromptNote(
    val id: String,
    val date: String,
    val text: String,
)

@Serializable
private data class PromptNotesPayload(val notes: List<PromptNote>)

/**
 * Serializes notes as JSON rather than wrapping them in ad-hoc
 * `[NOTE] ... [/NOTE]` markers.
 *
 * Note text is untrusted. JSON string escaping keeps quotes, newlines,
 * brackets, and even a literal `[/NOTE]` inside the note's `text` value, so a
 * note can never close the data block and have the remainder read as
 * instructions. The system prompt states that the whole payload is data.
 */
internal object PromptNotes {
    fun dataBlock(notes: List<PromptNote>): String =
        Json.encodeToString(PromptNotesPayload.serializer(), PromptNotesPayload(notes))
}
