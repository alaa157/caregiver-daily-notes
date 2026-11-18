package com.caregiver.mobile.ai

import com.caregiver.mobile.core.time.DateFormats
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

    /**
     * True when [date] falls inside the inclusive window.
     *
     * [date] is normally an ISO-8601 `LocalDate`, but a backend sending an
     * ISO date-time is accepted by parsing its date part. Since this filter
     * is what keeps data from outside the selected period away from the
     * model, a value with no parseable date is excluded rather than trusted.
     */
    fun contains(date: String?, endInclusive: LocalDate, periodDays: Int): Boolean {
        val parsed = DateFormats.parseIsoDate(date) ?: return false
        return !parsed.isBefore(start(endInclusive, periodDays)) && !parsed.isAfter(endInclusive)
    }
}
