package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.SignalDto

object SafetySignalEvaluator {
    const val HIGH_PAIN_THRESHOLD = 7
    const val MISSED_MED_COUNT_THRESHOLD = 2
    const val POOR_APPETITE_DAYS_THRESHOLD = 3

    fun evaluate(signals: List<SignalDto>): List<String> {
        val flags = mutableListOf<String>()
        if (signals.any { it.fallReported }) flags.add("FALL_REPORTED")
        if (signals.any { (it.pain ?: 0) >= HIGH_PAIN_THRESHOLD }) flags.add("HIGH_PAIN")
        val missedDates = signals.filter { it.medication == "missed" }
            .map { it.date }.distinct().size
        if (missedDates >= MISSED_MED_COUNT_THRESHOLD) flags.add("REPEATED_MISSED_MED")
        if (signals.any { it.medicationUnverified }) flags.add("MEDICATION_UNVERIFIED")
        val appetiteDates = signals.filter { it.poorAppetite }.map { it.date }.distinct().size
        if (appetiteDates >= POOR_APPETITE_DAYS_THRESHOLD) flags.add("POOR_APPETITE_DAYS")
        return flags
    }
}
