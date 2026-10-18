package com.caregiver.mobile.ai

import java.time.LocalDate

/**
 * The inclusive calendar-day window a summary covers.
 *
 * The selected period counts back from today, matching the backend's
 * SummaryService rule (`endInclusive - (periodDays - 1)`, inclusive on both
 * ends): a 7-day summary covers today plus the six days before it. Dates are
 * ISO-8601 `LocalDate` strings; "today" is the device-local date, consistent
 * with the history filters and with the note dates the clients send.
 *
 * Both the fetch (`from`/`to` query params) and [SummaryAiService] use this
 * rule, so a summary can never be labeled with one period while the model or
 * the deterministic flags actually see notes from outside it.
 */
object SummaryWindow {
    fun start(endInclusive: LocalDate, periodDays: Int): LocalDate =
        endInclusive.minusDays((periodDays - 1).toLong())

    /** True when the ISO-8601 [date] falls inside the inclusive window. */
    fun contains(date: String?, endInclusive: LocalDate, periodDays: Int): Boolean {
        val parsed = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return !parsed.isBefore(start(endInclusive, periodDays)) && !parsed.isAfter(endInclusive)
    }
}
