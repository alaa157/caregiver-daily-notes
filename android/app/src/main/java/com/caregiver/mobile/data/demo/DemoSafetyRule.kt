package com.caregiver.mobile.data.demo

/**
 * Demo rule: a recorded fall raises the safety red flag. This is the ONLY
 * automated safety decision in the offline demo. The stub never decides
 * anything beyond applying this rule; the real backend safety logic in
 * backend/ (SafetySignalEvaluator and friends) is untouched.
 */
object DemoSafetyRule {

    /**
     * Pain preview cutoff for the note-editor AlertSafety preview. Number
     * only, mirrored from the backend's own HIGH_PAIN_THRESHOLD (= 7,
     * SafetySignalEvaluator.java); no backend logic is copied or changed.
     */
    const val HIGH_PAIN_PREVIEW = 7

    /** True when any note in scope records a fall. */
    fun hasFallFlag(notes: List<NoteEntity>): Boolean = notes.any { it.fall }

    /** Editor preview shows before save when a fall is set or pain is high. */
    fun showSafetyPreview(fall: Boolean, pain: Int): Boolean =
        fall || pain >= HIGH_PAIN_PREVIEW
}
