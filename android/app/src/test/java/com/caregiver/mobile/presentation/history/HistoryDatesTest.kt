package com.caregiver.mobile.presentation.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 6: the date picker deals in UTC-midnight millis, so converting
 * through the device zone shifts the selected calendar day in western
 * zones. These tests pin UTC conversion while the existing
 * [HistoryViewModelTest] pins the unchanged API boundary (plain ISO
 * `LocalDate` strings for `from`/`to`).
 */
class HistoryDatesTest {

    @Test
    fun pickerMillisIsUtcMidnight() {
        assertEquals(
            Instant.parse("2026-10-07T00:00:00Z").toEpochMilli(),
            HistoryDates.toPickerMillis(LocalDate.of(2026, 10, 7)),
        )
    }

    @Test
    fun roundTripPreservesDayRegardlessOfDeviceZone() {
        val zones = listOf("UTC", "America/Los_Angeles", "Asia/Dubai", "Pacific/Kiritimati")
        val previous = TimeZone.getDefault()
        try {
            zones.forEach { id ->
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                val day = LocalDate.of(2026, 10, 7)
                assertEquals("zone=$id", day, HistoryDates.fromPickerMillis(HistoryDates.toPickerMillis(day)))
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun westernZoneKeepsSelectedDay() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val millis = HistoryDates.toPickerMillis(LocalDate.of(2026, 10, 7))

            // The old device-zone conversion shifted this to Oct 6 in LA.
            val shifted = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            assertEquals(LocalDate.of(2026, 10, 6), shifted)

            assertEquals(LocalDate.of(2026, 10, 7), HistoryDates.fromPickerMillis(millis))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
