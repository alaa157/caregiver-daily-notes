package com.caregiver.mobile.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-device note drafts (#906).
 *
 * A draft is keyed by person **and calendar day**, so reopening the editor for
 * the same person on the same day restores an in-progress note, while a new
 * day or another person never inherits it. Payloads are small JSON documents
 * in SharedPreferences; a corrupt value is treated as "no draft" rather than
 * crashing the editor.
 *
 * Storage is injected so JVM tests can use the in-memory implementation and
 * never touch Android.
 */
class NoteDraftStore(
    private val storage: Storage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    interface Storage {
        fun read(key: String): String?
        fun write(key: String, value: String?)
    }

    @Serializable
    data class Draft(
        val mood: String? = null,
        val appetite: String? = null,
        val sleep: String? = null,
        val mobility: String? = null,
        val medication: String? = null,
        val pain: Int? = null,
        val fall: Boolean? = null,
        val text: String = "",
        val savedAt: Long = 0L,
    )

    /** Null when missing or unreadable — never throws into the editor. */
    fun load(key: String): Draft? =
        storage.read(key)?.let { raw -> runCatching { json.decodeFromString(Draft.serializer(), raw) }.getOrNull() }

    fun save(key: String, draft: Draft) {
        storage.write(key, json.encodeToString(Draft.serializer(), draft))
    }

    fun clear(key: String) = storage.write(key, null)

    companion object {
        private const val PREFS = "note_drafts_v1"

        /** One key per person per day; the date is the ISO local date string. */
        fun key(recipientId: String, date: String): String = "note_draft_${recipientId}_$date"

        fun create(context: Context): NoteDraftStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return NoteDraftStore(
                object : Storage {
                    override fun read(key: String): String? = prefs.getString(key, null)
                    override fun write(key: String, value: String?) {
                        prefs.edit().apply {
                            if (value == null) remove(key) else putString(key, value)
                        }.apply()
                    }
                },
            )
        }

        /** Test-friendly store that survives for the lifetime of the returned object. */
        fun inMemory(): NoteDraftStore {
            val values = LinkedHashMap<String, String>()
            return NoteDraftStore(
                object : Storage {
                    override fun read(key: String): String? = values[key]
                    override fun write(key: String, value: String?) {
                        if (value == null) values.remove(key) else values[key] = value
                    }
                },
            )
        }
    }
}
