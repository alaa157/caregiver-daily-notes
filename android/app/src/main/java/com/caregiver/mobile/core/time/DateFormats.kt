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
}
