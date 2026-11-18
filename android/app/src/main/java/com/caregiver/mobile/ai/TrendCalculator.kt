package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.SignalDto
import kotlinx.serialization.Serializable

/** One metric's event count and sample size across two summary windows. */
@Serializable
data class TrendDto(
    val metric: String,
    val previousCount: Int,
    val currentCount: Int,
    val delta: Int,
    val previousSampleSize: Int,
    val currentSampleSize: Int,
)

/**
 * Compares the current summary window with the previous one over the
 * structured signals — four counts, no model involvement. Mirrors the
 * backend `TrendCalculator`: same metrics, same order, same
 * recorded/event rules.
 */
object TrendCalculator {
    fun compute(current: List<SignalDto>, previous: List<SignalDto>): List<TrendDto> = listOf(
        compare("FALL_COUNT", countNotes(previous, { it.fallRecorded }, { it.fallReported }),
            countNotes(current, { it.fallRecorded }, { it.fallReported })),
        compare(
            "HIGH_PAIN_COUNT",
            countNotes(
                previous,
                { it.painRecorded && it.pain != null },
                { (it.pain ?: 0) >= SafetySignalEvaluator.HIGH_PAIN_THRESHOLD },
            ),
            countNotes(
                current,
                { it.painRecorded && it.pain != null },
                { (it.pain ?: 0) >= SafetySignalEvaluator.HIGH_PAIN_THRESHOLD },
            ),
        ),
        compare(
            "MISSED_MED_COUNT",
            countDates(
                previous,
                { it.medication.isNotBlank() && !it.medicationUnverified },
                { it.medication == "missed" },
            ),
            countDates(
                current,
                { it.medication.isNotBlank() && !it.medicationUnverified },
                { it.medication == "missed" },
            ),
        ),
        compare(
            "POOR_APPETITE_COUNT",
            countDates(previous, { it.appetiteRecorded }, { it.poorAppetite }),
            countDates(current, { it.appetiteRecorded }, { it.poorAppetite }),
        ),
    )

    private fun compare(metric: String, previous: MetricCounts, current: MetricCounts) =
        TrendDto(
            metric = metric,
            previousCount = previous.events,
            currentCount = current.events,
            delta = current.events - previous.events,
            previousSampleSize = previous.sampleSize,
            currentSampleSize = current.sampleSize,
        )

    private fun countNotes(
        notes: List<SignalDto>,
        recorded: (SignalDto) -> Boolean,
        event: (SignalDto) -> Boolean,
    ): MetricCounts {
        var events = 0
        var sampleSize = 0
        for (note in notes) {
            if (recorded(note)) {
                sampleSize++
                if (event(note)) events++
            }
        }
        return MetricCounts(events, sampleSize)
    }

    private fun countDates(
        notes: List<SignalDto>,
        recorded: (SignalDto) -> Boolean,
        event: (SignalDto) -> Boolean,
    ): MetricCounts {
        val sampledDates = mutableSetOf<String>()
        val eventDates = mutableSetOf<String>()
        for (note in notes) {
            if (recorded(note)) {
                sampledDates.add(note.date)
                if (event(note)) eventDates.add(note.date)
            }
        }
        return MetricCounts(eventDates.size, sampledDates.size)
    }

    private data class MetricCounts(val events: Int, val sampleSize: Int)
}
