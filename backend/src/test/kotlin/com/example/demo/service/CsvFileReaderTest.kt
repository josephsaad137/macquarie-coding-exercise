package com.example.demo.service

import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CsvFileReaderTest {
    private val reader = CsvFileReader()

    @Test
    fun `reads quoted commas escaped quotes and embedded newlines`() {
        val csv = """
            contract,timestamp,open,high,low,close,volume
            "ES, H25",2025-01-02T09:30:00,100,102,99,101,10
            "ES ""special""
            contract",2025-01-02T09:31:00,101,103,100,102,11
        """.trimIndent()

        val (headers, rows) = reader.read(csv.toByteArray()) { table ->
            table.headers to table.rows.toList()
        }

        assertEquals(
            listOf("contract", "timestamp", "open", "high", "low", "close", "volume"),
            headers,
        )
        assertEquals(
            listOf(
                listOf("ES, H25", "2025-01-02T09:30:00", "100", "102", "99", "101", "10"),
                listOf("ES \"special\"\ncontract", "2025-01-02T09:31:00", "101", "103", "100", "102", "11"),
            ),
            rows,
        )
    }

    @Test
    fun `rejects an unterminated quoted field`() {
        assertFailsWith<IOException> {
            reader.read("contract,timestamp\n\"unterminated".toByteArray()) { table ->
                table.rows.toList()
            }
        }
    }
}
