package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.R
import com.caregiver.mobile.data.api.NoteDetailDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chip label resource mappings and fallbacks for blank/unknown values. */
class OptionLabelsTest {

    @Test
    fun labelsResolveToResources() {
        assertEquals(R.string.note_option_mood_good, OptionLabels.labelRes(OptionGroup.Mood, "good"))
        assertEquals(R.string.note_option_sleep_broken, OptionLabels.labelRes(OptionGroup.Sleep, "broken"))
        assertEquals(
            R.string.note_option_medication_unsure,
            OptionLabels.labelRes(OptionGroup.Medication, "unsure"),
        )
        assertEquals(
            R.string.note_option_mobility_bed,
            OptionLabels.labelRes(OptionGroup.Mobility, "bed"),
        )
    }

    @Test
    fun editorLabelsResolveToResources() {
        assertEquals(R.string.note_option_mood_good, OptionLabels.labelRes(OptionGroup.Mood, "good"))
        assertEquals(
            R.string.note_option_medication_unsure,
            OptionLabels.labelRes(OptionGroup.Medication, "unsure"),
        )
    }

    @Test
    fun medicationLabelsResolveToResourcesForAllEditorValues() {
        // The note detail grid renders medicationTaken through these labels,
        // so every wire value the editor records must resolve to a resource.
        assertEquals(
            R.string.note_option_medication_taken,
            OptionLabels.labelRes(OptionGroup.Medication, "taken"),
        )
        assertEquals(
            R.string.note_option_medication_missed,
            OptionLabels.labelRes(OptionGroup.Medication, "missed"),
        )
        assertEquals(
            R.string.note_option_medication_unsure,
            OptionLabels.labelRes(OptionGroup.Medication, "unsure"),
        )
    }

    @Test
    fun unknownValuesFallBackToRaw() {
        assertEquals(
            "weird",
            optionLabelFallback("weird", OptionLabels.labelRes(OptionGroup.Mood, "weird")),
        )
    }

    @Test
    fun blankValuesFallBackToEmpty() {
        assertEquals("", optionLabelFallback("  ", OptionLabels.labelRes(OptionGroup.Mood, "  ")))
    }

    @Test
    fun labelResCoversEveryKnownPair() {
        val pairs = listOf(
            OptionGroup.Mood to listOf(
                "calm" to R.string.note_option_mood_calm,
                "happy" to R.string.note_option_mood_happy,
                "anxious" to R.string.note_option_mood_anxious,
                "agitated" to R.string.note_option_mood_agitated,
                "sad" to R.string.note_option_mood_sad,
                "confused" to R.string.note_option_mood_confused,
                "good" to R.string.note_option_mood_good,
                "fair" to R.string.note_option_mood_fair,
                "bad" to R.string.note_option_mood_bad,
            ),
            OptionGroup.Appetite to listOf(
                "good" to R.string.note_option_appetite_good,
                "fair" to R.string.note_option_appetite_fair,
                "poor" to R.string.note_option_appetite_poor,
                "refused" to R.string.note_option_appetite_refused,
                "reduced" to R.string.note_option_appetite_reduced,
            ),
            OptionGroup.Sleep to listOf(
                "restful" to R.string.note_option_sleep_restful,
                "restless" to R.string.note_option_sleep_restless,
                "poor" to R.string.note_option_sleep_poor,
                "sleepless" to R.string.note_option_sleep_sleepless,
                "good" to R.string.note_option_sleep_good,
                "broken" to R.string.note_option_sleep_broken,
            ),
            OptionGroup.Mobility to listOf(
                "independent" to R.string.note_option_mobility_independent,
                "assisted" to R.string.note_option_mobility_assisted,
                "bedridden" to R.string.note_option_mobility_bedridden,
                "walks" to R.string.note_option_mobility_walks,
                "bed" to R.string.note_option_mobility_bed,
            ),
            OptionGroup.Medication to listOf(
                "taken" to R.string.note_option_medication_taken,
                "missed" to R.string.note_option_medication_missed,
                "unsure" to R.string.note_option_medication_unsure,
            ),
        )
        var count = 0
        pairs.forEach { (group, values) ->
            values.forEach { (value, resId) ->
                assertEquals(resId, OptionLabels.labelRes(group, value))
                assertNotNull(OptionLabels.labelRes(group, value))
                count++
            }
        }
        assertEquals(28, count)
    }

    @Test
    fun labelResReturnsNullForUnknownValues() {
        assertNull(OptionLabels.labelRes(OptionGroup.Mood, "unknown"))
        assertNull(OptionLabels.labelRes(OptionGroup.Medication, "taken "))
    }

    @Test
    fun currentVocabularyMatchesTheWidenedLists() {
        assertEquals(
            listOf("calm", "happy", "anxious", "agitated", "sad", "confused"),
            NoteOptions.values(OptionGroup.Mood),
        )
        assertEquals(
            listOf("good", "fair", "poor", "refused"),
            NoteOptions.values(OptionGroup.Appetite),
        )
        assertEquals(
            listOf("restful", "restless", "poor", "sleepless"),
            NoteOptions.values(OptionGroup.Sleep),
        )
        assertEquals(
            listOf("independent", "assisted", "bedridden"),
            NoteOptions.values(OptionGroup.Mobility),
        )
        assertEquals(
            listOf("taken", "missed", "unsure"),
            NoteOptions.values(OptionGroup.Medication),
        )
    }

    @Test
    fun knownMergesCurrentAndLegacyValuesWithoutDuplicates() {
        val known = NoteOptions.known(OptionGroup.Mood)
        assertEquals(
            listOf("calm", "happy", "anxious", "agitated", "sad", "confused", "good", "fair", "bad"),
            known,
        )
        assertEquals(known.size, known.toSet().size)

        assertTrue(NoteOptions.known(OptionGroup.Appetite).containsAll(listOf("reduced")))
        assertTrue(NoteOptions.known(OptionGroup.Sleep).containsAll(listOf("good", "broken")))
        assertTrue(NoteOptions.known(OptionGroup.Mobility).containsAll(listOf("walks", "bed")))
        assertTrue(NoteOptions.known(OptionGroup.Medication).contains("none"))
    }

    private fun detail(
        mood: String = "",
        appetite: String = "",
        sleep: String = "",
        mobility: String = "",
        medicationTaken: String = "",
        pain: Int = 0,
        fall: Boolean = false,
        painRecorded: Boolean = false,
        fallRecorded: Boolean = false,
    ) = NoteDetailDto(
        id = "n1",
        recipientId = "r1",
        date = "2026-11-15",
        mood = mood,
        appetite = appetite,
        sleep = sleep,
        mobility = mobility,
        medicationTaken = medicationTaken,
        pain = pain,
        fall = fall,
        text = "note",
        addenda = emptyList(),
        painRecorded = painRecorded,
        fallRecorded = fallRecorded,
    )

    @Test
    fun visibleStatsIsEmptyWhenNothingWasAnswered() {
        assertEquals(emptyList<StatField>(), visibleStats(detail()))
    }

    @Test
    fun visibleStatsShowsOnlyTheAnsweredOption() {
        assertEquals(listOf(StatField.Mood), visibleStats(detail(mood = "good")))
    }

    @Test
    fun visibleStatsIncludesRecordedZeroPain() {
        assertEquals(
            listOf(StatField.Pain),
            visibleStats(detail(pain = 0, painRecorded = true)),
        )
    }

    @Test
    fun visibleStatsIncludesRecordedFalseFall() {
        assertEquals(
            listOf(StatField.Fall),
            visibleStats(detail(fall = false, fallRecorded = true)),
        )
    }

    @Test
    fun visibleStatsHidesPainWhenItsFlagIsOffEvenWithAValue() {
        assertEquals(
            emptyList<StatField>(),
            visibleStats(detail(pain = 5, painRecorded = false)),
        )
    }

    @Test
    fun visibleStatsReturnsAllSevenFieldsInEnumOrder() {
        assertEquals(
            StatField.entries.toList(),
            visibleStats(
                detail(
                    mood = "good",
                    appetite = "good",
                    sleep = "good",
                    mobility = "walks",
                    medicationTaken = "taken",
                    pain = 3,
                    fall = true,
                    painRecorded = true,
                    fallRecorded = true,
                ),
            ),
        )
    }
}
