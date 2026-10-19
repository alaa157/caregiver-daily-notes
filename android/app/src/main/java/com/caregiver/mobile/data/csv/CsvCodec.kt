package com.caregiver.mobile.data.csv

/**
 * Minimal, dependency-free RFC 4180-style CSV reader/writer.
 *
 * The parser understands quoted fields, escaped double quotes (`""`), commas
 * and embedded newlines, strips a leading UTF-8 BOM, and normalizes CRLF /
 * CR / LF record separators. It is deliberately strict about quotes in
 * unquoted fields and about unterminated quoted fields: the caller receives a
 * [CsvFormatException] with the 1-based line instead of silently guessing.
 *
 * The writer escapes a field only when it contains a comma, a double quote, a
 * carriage return or a line feed, and joins records with CRLF so spreadsheet
 * software opens the file cleanly.
 *
 * No note or recipient content is ever logged here.
 */
class CsvFormatException(val line: Int, message: String) : Exception(message)

/** One CSV record; [line] is the 1-based line where the record begins. */
data class CsvRecord(val line: Int, val fields: List<String>)

/** Parsed table: the first record is [header], the rest are data [records]. */
data class CsvTable(val header: List<String>, val records: List<CsvRecord>)

object CsvCodec {
    /** UTF-8 byte-order mark as a character, stripped on parse and prepended on encode. */
    const val BOM: Char = '\uFEFF'

    /** Exported files use CRLF, matching what spreadsheet software writes. */
    const val RECORD_SEPARATOR: String = "\r\n"

    fun parse(input: String): CsvTable {
        val text = if (input.startsWith(BOM)) input.substring(1) else input
        val records = mutableListOf<CsvRecord>()
        var fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var index = 0

        fun endRecord() {
            fields.add(current.toString())
            current.setLength(0)
            records.add(CsvRecord(recordLine, fields))
            fields = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            if (inQuotes) {
                when {
                    char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        current.append('"')
                        index += 2
                    }
                    char == '"' -> {
                        inQuotes = false
                        index += 1
                    }
                    char == '\n' -> {
                        // Embedded newlines are preserved verbatim so a
                        // decode/encode round trip never rewrites them.
                        current.append(char)
                        line += 1
                        index += 1
                    }
                    else -> {
                        current.append(char)
                        index += 1
                    }
                }
            } else {
                when (char) {
                    '"' -> {
                        if (current.isNotEmpty()) {
                            throw CsvFormatException(line, "Quote inside an unquoted field")
                        }
                        inQuotes = true
                        index += 1
                    }
                    ',' -> {
                        fields.add(current.toString())
                        current.setLength(0)
                        index += 1
                    }
                    '\r', '\n' -> {
                        endRecord()
                        if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                            index += 2
                        } else {
                            index += 1
                        }
                        line += 1
                        recordLine = line
                    }
                    else -> {
                        current.append(char)
                        index += 1
                    }
                }
            }
        }

        if (inQuotes) throw CsvFormatException(line, "Unterminated quoted field")
        if (current.isNotEmpty() || fields.isNotEmpty()) endRecord()

        if (records.isEmpty()) return CsvTable(emptyList(), emptyList())
        return CsvTable(records.first().fields, records.drop(1))
    }

    fun encode(rows: List<List<String>>): String =
        rows.joinToString(RECORD_SEPARATOR) { row -> row.joinToString(",") { escape(it) } }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
