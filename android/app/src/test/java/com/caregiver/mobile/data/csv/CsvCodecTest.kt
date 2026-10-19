package com.caregiver.mobile.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** RFC 4180 tokenizing: quotes, escapes, embedded newlines, BOM and line endings. */
class CsvCodecTest {

    @Test
    fun parsesSimpleRowsAndReportsRecordLines() {
        val table = CsvCodec.parse("a,b\r\n1,2\r\n3,4")

        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(2, 3), table.records.map { it.line })
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), table.records.map { it.fields })
    }

    @Test
    fun stripsALeadingBom() {
        val table = CsvCodec.parse("\uFEFFa,b\n1,2")

        assertEquals(listOf("a", "b"), table.header)
    }

    @Test
    fun acceptsLfCrAndCrlfLineEndings() {
        val table = CsvCodec.parse("a,b\n1,2\r3,4\r\n5,6")

        assertEquals(3, table.records.size)
        assertEquals(listOf("5", "6"), table.records.last().fields)
    }

    @Test
    fun keepsCommasInsideQuotedFields() {
        val table = CsvCodec.parse("name,text\r\n\"Mona, A.\",plain")

        assertEquals(listOf("Mona, A.", "plain"), table.records.single().fields)
    }

    @Test
    fun unescapesDoubledQuotes() {
        val table = CsvCodec.parse("a\r\n\"say \"\"hi\"\"\"")

        assertEquals("say \"hi\"", table.records.single().fields.single())
    }

    @Test
    fun keepsEmbeddedNewlinesVerbatim() {
        val table = CsvCodec.parse("a,b\r\n\"l1\r\nl2\",x")

        assertEquals("l1\r\nl2", table.records.single().fields.first())
        assertEquals("x", table.records.single().fields.last())
    }

    @Test
    fun trailingNewlineDoesNotCreateAPhantomRow() {
        assertEquals(1, CsvCodec.parse("a\n1\n").records.size)
        assertEquals(1, CsvCodec.parse("a\r\n1\r\n").records.size)
    }

    @Test
    fun unterminatedQuoteThrowsWithTheLine() {
        val failure = assertThrows(CsvFormatException::class.java) {
            CsvCodec.parse("a,b\n1,\"open")
        }

        assertEquals(2, failure.line)
    }

    @Test
    fun quoteInsideAnUnquotedFieldThrows() {
        assertThrows(CsvFormatException::class.java) {
            CsvCodec.parse("a\nab\"c")
        }
    }

    @Test
    fun escapesOnlyWhenNeeded() {
        assertEquals("plain", CsvCodec.escape("plain"))
        assertEquals("\"a,b\"", CsvCodec.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvCodec.escape("say \"hi\""))
        assertEquals("\"l1\nl2\"", CsvCodec.escape("l1\nl2"))
        assertEquals("\"l1\rl2\"", CsvCodec.escape("l1\rl2"))
    }

    @Test
    fun encodeAndParseRoundTripUnicodeAndSpecialCharacters() {
        val rows = listOf(
            listOf("name", "text"),
            listOf("منى", "أكل جيد، شرب الماء"),
            listOf("Mona", "line one\nline \"two\", three"),
        )

        val table = CsvCodec.parse(CsvCodec.encode(rows))

        assertEquals(rows, listOf(table.header) + table.records.map { it.fields })
    }

    @Test
    fun emptyInputParsesToAnEmptyTable() {
        val table = CsvCodec.parse("")

        assertEquals(emptyList<String>(), table.header)
        assertEquals(0, table.records.size)
    }
}
