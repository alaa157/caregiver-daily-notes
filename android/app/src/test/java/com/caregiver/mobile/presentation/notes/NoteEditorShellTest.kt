package com.caregiver.mobile.presentation.notes

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic for the #901 editor shell. The visible counter is driven by the
 * ViewModel's existing [EditorState.answeredCount] — nothing here decides how
 * many questions there are independently of that function.
 */
class NoteEditorShellTest {

    @Test
    fun emptyFormHasNothingToLose() {
        assertFalse(hasUnsavedChanges(EditorState()))
        assertFalse(shouldConfirmClose(EditorState()))
    }

    @Test
    fun anyAnsweredFieldOrTextMeansUnsavedChanges() {
        assertTrue(hasUnsavedChanges(EditorState(mood = "happy")))
        assertTrue(hasUnsavedChanges(EditorState(pain = 0)))
        assertTrue(hasUnsavedChanges(EditorState(fall = false)))
        assertTrue(hasUnsavedChanges(EditorState(text = "a note")))
        // Whitespace-only free text is not a change.
        assertFalse(hasUnsavedChanges(EditorState(text = "   ")))
    }

    @Test
    fun dirtyFormConfirmsCloseEvenWhenTheDraftWasAutosaved() {
        val dirty = EditorState(mood = "happy", text = "draft", draftSavedAt = 123L)
        // #915: #906's autosave no longer suppresses the prompt.
        assertTrue(shouldConfirmClose(dirty, autosaved = false))
        assertTrue(shouldConfirmClose(dirty, autosaved = true))
        assertTrue(shouldConfirmClose(dirty))
        // A clean form never asks, whatever the flag says.
        assertFalse(shouldConfirmClose(EditorState(), autosaved = true))
    }

    @Test
    fun progressComesFromAnsweredCountAndIsBounded() {
        assertEquals(0f, editorProgressFraction(0))
        assertEquals(1f, editorProgressFraction(EDITOR_QUESTION_COUNT))
        assertEquals(EDITOR_QUESTION_COUNT.toFloat() / 7f, editorProgressFraction(3))
        // Defensive bounds: a bad caller can never draw past the bar.
        assertEquals(0f, editorProgressFraction(-1))
        assertEquals(1f, editorProgressFraction(99))
    }

    @Test
    fun everyQuestionCountsExceptFreeText() {
        val all = EditorState(
            mood = "happy",
            appetite = "good",
            sleep = "good",
            mobility = "walks",
            medication = "taken",
            pain = 0,
            fall = false,
            text = "not counted",
        )
        assertEquals(EDITOR_QUESTION_COUNT, all.answeredCount())
        assertEquals(1f, editorProgressFraction(all.answeredCount()))
        assertEquals(
            EDITOR_QUESTION_COUNT - 1,
            all.copy(fall = null).answeredCount(),
        )
    }

    @Test
    fun draftTimeIsStableAndLocaleNeutral() {
        assertEquals("09:05", draftTimeLabel(LocalTime.of(9, 5)))
        assertEquals("23:59", draftTimeLabel(LocalTime.of(23, 59)))
    }
}
