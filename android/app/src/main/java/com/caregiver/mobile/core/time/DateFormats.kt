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
}
