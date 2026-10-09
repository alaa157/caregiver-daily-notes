package com.caregiver.mobile.presentation.notes

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.R
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import com.caregiver.mobile.data.demo.DemoSafetyRule
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Editor states: idle, saving, saved, error (text preserved). */
data class EditorDemoState(
    val recipientName: String = "",
    val mood: String? = null,
    val appetite: String? = null,
    val sleep: String? = null,
    val mobility: String? = null,
    val medication: String? = null,
    val pain: Int = 0,
    val fall: Boolean = false,
    val text: String = "",
    val invalid: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val savedId: String? = null,
)

class EditorDemoViewModel(
    private val recipientId: String,
    private val recipients: DemoRecipientRepository,
    private val notes: DemoNoteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorDemoState())
    val state: StateFlow<EditorDemoState> = _state.asStateFlow()

    /** Live AlertSafety preview before save (falls or high pain). */
    val preview: Boolean
        get() = DemoSafetyRule.showSafetyPreview(_state.value.fall, _state.value.pain)

    init {
        viewModelScope.launch {
            recipients.getRecipient(recipientId)?.let { person ->
                _state.value = _state.value.copy(recipientName = person.name)
            }
        }
    }

    fun select(group: OptionGroup, value: String) {
        _state.value = when (group) {
            OptionGroup.Mood -> _state.value.copy(mood = value.takeIf { it != _state.value.mood })
            OptionGroup.Appetite -> _state.value.copy(appetite = value.takeIf { it != _state.value.appetite })
            OptionGroup.Sleep -> _state.value.copy(sleep = value.takeIf { it != _state.value.sleep })
            OptionGroup.Mobility -> _state.value.copy(mobility = value.takeIf { it != _state.value.mobility })
            OptionGroup.Medication -> _state.value.copy(medication = value.takeIf { it != _state.value.medication })
        }.copy(invalid = false, failed = false)
    }

    fun setFall(value: Boolean) {
        _state.value = _state.value.copy(fall = value, invalid = false, failed = false)
    }

    fun setPain(value: Int) {
        _state.value = _state.value.copy(pain = value.coerceIn(0, 10), invalid = false, failed = false)
    }

    fun onText(value: String) {
        _state.value = _state.value.copy(text = value, invalid = false, failed = false)
    }

    /**
     * Save the immutable original. Mood + free text are required (spec copy
     * owns no field-error strings, so the shared error state stands in --
     * flagged). Entered text is always preserved on failure.
     */
    fun submit(context: Context) {
        val s = _state.value
        if (s.busy || s.savedId != null) return
        if (s.mood == null || s.text.isBlank()) {
            _state.value = s.copy(invalid = true)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, failed = false)
            try {
                val id = notes.saveNote(
                    recipientId = recipientId,
                    date = LocalDate.now().toString(),
                    mood = s.mood,
                    appetite = s.appetite ?: "",
                    sleep = s.sleep ?: "",
                    mobility = s.mobility ?: "",
                    medicationTaken = s.medication ?: "",
                    pain = s.pain,
                    fall = s.fall,
                    text = s.text,
                )
                Toast.makeText(context, R.string.note_saved, Toast.LENGTH_SHORT).show()
                _state.value = _state.value.copy(busy = false, savedId = id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, failed = true)
            }
        }
    }
}
