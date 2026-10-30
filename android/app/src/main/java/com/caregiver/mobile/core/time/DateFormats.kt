package com.caregiver.mobile.core.time

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates render with Latin digits in both languages ("7 أكتوبر", "Oct 7"),
 * matching the design boards. The `ar-u-nu-latn` Unicode extension forces
 * Latin digits because the plain `ar` locale would render ٧ أكتوبر.
 */
object DateFormats {
    /**
     * Parses a note or signal date, tolerating a date-time by taking its date
     * part. The documented wire format is an ISO local date, but a backend
     * that sends `2026-10-07T18:30:00`, a fractional second, or an offset
     * must not make the note disappear from a summary window.
     *
     * Returns null for blank or genuinely malformed values so callers can
     * decide whether to omit or exclude them.
     */
    fun parseIsoDate(raw: String?): LocalDate? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val datePart = value.substringBefore('T').substringBefore(' ')
        return try {
            LocalDate.parse(datePart)
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }

    fun dayMonth(date: LocalDate, locale: Locale): String {
        val effective = if (locale.language == "ar") {
            Locale.forLanguageTag("ar-u-nu-latn")
        } else {
            locale
        }
        val pattern = if (locale.language == "ar") "d MMMM" else "MMM d"
        return DateTimeFormatter.ofPattern(pattern, effective).format(date)
    }

    /**
     * Full day for history rows ("7 أكتوبر 2026", "Oct 7, 2026"). Query
     * parameters always stay ISO — this is display only, with a raw
     * fallback if the server ever sends a non-ISO date.
     */
    fun historyDay(isoDate: String, arabic: Boolean): String {
        val parsed = try {
            LocalDate.parse(isoDate)
        } catch (e: java.time.format.DateTimeParseException) {
            return isoDate
        }
        val locale = if (arabic) {
            Locale.forLanguageTag("ar-u-nu-latn")
        } else {
            Locale.ENGLISH
        }
        val pattern = if (arabic) "d MMMM yyyy" else "MMM d, yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(parsed)
    }

    /**
     * Addendum creation date for the attachments timeline. Addenda arrive as
     * ISO date-times (`2026-10-07T18:30:00`, fractional seconds, or trailing
     * `Z`/offset) or plain ISO dates. Returns the [historyDay] rendering of
     * the date part, or null when the value is blank or unparsable so callers
     * omit the date line instead of showing a misleading date.
     */
    fun addendumDate(createdAt: String, arabic: Boolean): String? {
        val parsed = parseIsoDate(createdAt) ?: return null
        // Re-render through the shared formatter so both languages stay
        // consistent with history rows.
        return historyDay(parsed.toString(), arabic)
    }
}
