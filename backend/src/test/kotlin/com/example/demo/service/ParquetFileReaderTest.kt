package com.example.demo.service

import java.time.LocalDateTime
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ParquetFileReaderTest {
    private val reader = ParquetFileReader()

    @Test
    fun `normalizes sample Chicago wall timestamps without treating nanoseconds as milliseconds`() {
        val content = javaClass.getResourceAsStream("/sample-data/ESH25-minute.parquet")
            ?.use { it.readBytes() }
            ?: error("Missing test fixture: sample-data/ESH25-minute.parquet")

        val timestamp = reader.read(content) { table ->
            val timestampColumn = table.headers.indexOf("timestamp_chicago_wall")
            assertTrue(timestampColumn >= 0)
            LocalDateTime.parse(table.rows.first()[timestampColumn])
        }
        assertTrue(timestamp.year in 2000..2100, "Expected a market-data year, got $timestamp")
    }
}
