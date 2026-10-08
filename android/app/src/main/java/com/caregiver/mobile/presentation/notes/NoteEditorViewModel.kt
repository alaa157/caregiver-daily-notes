package com.caregiver.mobile.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.R
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.ApiErrors
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateNoteRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface EditorError {
    data object MoodRequired : EditorError
    data object TextRequired : EditorError
    data class Rejected(val code: String?) : EditorError
    data object Unreachable : EditorError
}

/**
 * Maps save-failure codes to localized copy. The only reachable 422 on note
 * creation is the one-note-per-day rule (all field rules are enforced
 * client-side first), so it gets specific copy; everything else is generic.
 */
object EditorErrorText {
    fun res(code: String?): Int = when (code) {
        "VALIDATION_ERROR" -> R.string.editor_error_duplicate_day
        else -> R.string.editor_error_generic
    }
}

data class EditorState(
    val mood: String? = null,
    val appetite: String? = null,
    val sleep: String? = null,
    val mobility: String? = null,
    val medication: String? = null,
    val pain: Int = 0,
    val fall: Boolean = false,
    val text: String = "",
    val moodError: EditorError.MoodRequired? = null,
    val textError: EditorError.TextRequired? = null,
    val formError: EditorError? = null,
    val busy: Boolean = false,
    val savedId: String? = null,
)

/**
 * Note editor (board 5). Mood and free text are required; unselected groups
 * submit as "" (the backend stores those verbatim). Pain is clamped 0-10.
 */
class NoteEditorViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    fun select(group: OptionGroup, value: String) {
        val current = _state.value.current(group)
        val next = if (current == value) null else value
        _state.value = _state.value.with(group, next).copy(formError = null)
    }

    fun setPain(value: Int) {
        _state.value = _state.value.copy(pain = value.coerceIn(MIN_PAIN, MAX_PAIN))
    }

    fun setFall(value: Boolean) {
        _state.value = _state.value.copy(fall = value)
    }

    fun onText(value: String) {
        _state.value = _state.value.copy(text = value, textError = null, formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.mood == null) {
            _state.value = current.copy(moodError = EditorError.MoodRequired)
            return
        }
        if (current.text.isBlank()) {
            _state.value = current.copy(textError = EditorError.TextRequired)
            return
        }
        _state.value = current.copy(busy = true, formError = null)
        exec.launch {
            try {
                val created = repository.authorized {
                    apis.notes().create(
                        CreateNoteRequest(
                            recipientId = recipientId,
                            mood = current.mood,
                            appetite = current.appetite.orEmpty(),
                            sleep = current.sleep.orEmpty(),
                            mobility = current.mobility.orEmpty(),
                            medicationTaken = current.medication.orEmpty(),
                            pain = current.pain,
                            fall = current.fall,
                            text = current.text,
                        ),
                    )
                }
                _state.value = _state.value.copy(busy = false, savedId = created.id)
            } catch (e: LoggedOutException) {
                _state.value = _state.value.copy(busy = false)
            } catch (e: HttpException) {
                _state.value = _state.value.copy(
                    busy = false,
                    formError = EditorError.Rejected(ApiErrors.parse(e)?.code),
                )
            } catch (e: IOException) {
                _state.value = _state.value.copy(busy = false, formError = EditorError.Unreachable)
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(busy = false)
                throw e
            }
        }
    }

    private fun EditorState.current(group: OptionGroup): String? = when (group) {
        OptionGroup.Mood -> mood
        OptionGroup.Appetite -> appetite
        OptionGroup.Sleep -> sleep
        OptionGroup.Mobility -> mobility
        OptionGroup.Medication -> medication
    }

    private fun EditorState.with(group: OptionGroup, value: String?): EditorState = when (group) {
        OptionGroup.Mood -> copy(mood = value, moodError = null)
        OptionGroup.Appetite -> copy(appetite = value)
        OptionGroup.Sleep -> copy(sleep = value)
        OptionGroup.Mobility -> copy(mobility = value)
        OptionGroup.Medication -> copy(medication = value)
    }

    companion object {
        const val MIN_PAIN = 0
        const val MAX_PAIN = 10
    }
}
