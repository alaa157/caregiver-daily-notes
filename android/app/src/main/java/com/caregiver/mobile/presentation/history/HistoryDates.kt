package com.caregiver.mobile.presentation.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Date-picker conversions for history filters (Task 6).
 *
 * Material3 `DatePickerState.selectedDateMillis` is midnight UTC of the
 * chosen calendar day. Converting through the device zone shifts the day in
 * western zones (e.g. picking Oct 7 in America/Los_Angeles yields Oct 6
 * 17:00 local), so both directions go through UTC. The API contract is
 * unaffected: [com.caregiver.mobile.presentation.history.HistoryViewModel]
 * still sends plain ISO `LocalDate` strings as `from`/`to`.
 */
object HistoryDates {
    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
