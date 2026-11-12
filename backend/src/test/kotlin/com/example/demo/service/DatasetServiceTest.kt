package com.example.demo.service

import com.example.demo.persistence.InMemoryDatasetRepository
import com.example.demo.service.model.DatasetFrequency
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus

class DatasetServiceTest {
    private val service = DatasetService(InMemoryDatasetRepository())

    @Test
    fun `rejects an empty Parquet dataset`() {
        val exception = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.importDataset("empty.parquet", "parquet", byteArrayOf())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `imports valid CSV rows and reports malformed and duplicate rows`() {
        val csv = """
            Contract,Timestamp,Open,High,Low,Close,Volume
            ESH25,2025-01-02T09:30:00,100,102,99,101,10
            ESH25,2025-01-02T09:30:00,100,102,99,101,10
            ESH25,2025-01-02T09:31:00,,102,99,101,10
            ESH25,2025-01-02T09:32:00,100,102,99,101,-1
            ESH25,2025-01-02T09:33:00,100,99,98,101,10
        """.trimIndent()

        val imported = service.importDataset("bars.csv", "csv", csv.toByteArray())

        assertEquals("COMPLETED_WITH_WARNINGS", imported.status)
        assertEquals(1, imported.rowsImported)
        assertEquals(4, imported.rowsRejected)
        assertEquals("bars.csv", imported.filename)
        assertEquals(
            listOf("DUPLICATE_BAR", "MALFORMED_RECORD", "INVALID_VOLUME", "INVALID_OHLC"),
            service.qualityIssues(imported.id).map { it.code },
        )
    }

    @Test
    fun `distinguishes conflicting records from exact duplicates`() {
        val imported = service.importDataset(
            "conflicting.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:30:00,100,105,99,104,10
            """.trimIndent().toByteArray(),
        )

        assertEquals(1, imported.rowsImported)
        assertEquals(2, imported.rowsRejected)
        assertEquals(
            listOf("DUPLICATE_BAR", "CONFLICTING_BAR"),
            service.qualityIssues(imported.id).map { it.code },
        )
    }

    @Test
    fun `imports CSV fields using normalized sample column names`() {
        val csv = """
            contract_symbol,timestamp_chicago_wall,open,high,low,close,volume
            ESH25,2025-01-02 09:30:00,100,102,99,101,10
        """.trimIndent()

        val imported = service.importDataset("ESH25.csv", "csv", csv.toByteArray())

        assertEquals(1, imported.rowsImported)
        assertEquals(0, imported.rowsRejected)
        assertEquals("COMPLETED", imported.status)
    }

    @Test
    fun `reports missing minute bars without counting them as rejected rows`() {
        val imported = service.importDataset(
            "minute-bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:33:00,101,103,100,102,10
            """.trimIndent().toByteArray(),
            DatasetFrequency.MINUTE,
        )

        assertEquals("COMPLETED_WITH_WARNINGS", imported.status)
        assertEquals(2, imported.rowsImported)
        assertEquals(0, imported.rowsRejected)
        val gap = service.qualityIssues(imported.id).single()
        assertEquals("MISSING_TIMESTAMP", gap.code)
        assertEquals(null, gap.rowNumber)
        assertEquals("ESH25", gap.contract)
        assertEquals("2025-01-02T09:31", gap.timestamp.toString())
        assertTrue(gap.message.contains("2 missing minute bar(s)"))
    }

    @Test
    fun `reports missing calendar dates in daily datasets`() {
        val imported = service.importDataset(
            "daily-bars.csv",
            "csv",
            """
                contract,date,open,high,low,close,volume
                ESH25,2025-01-03,100,102,99,101,10
                ESH25,2025-01-06,101,103,100,102,10
            """.trimIndent().toByteArray(),
            DatasetFrequency.DAILY,
        )

        val gap = service.qualityIssues(imported.id).single()
        assertEquals("MISSING_TIMESTAMP", gap.code)
        assertEquals("2025-01-04T00:00", gap.timestamp.toString())
        assertTrue(gap.message.contains("2 missing daily bar(s)"))
        assertEquals(0, imported.rowsRejected)
    }

    @Test
    fun `checks each contract independently and ignores rejected duplicate timestamps`() {
        val imported = service.importDataset(
            "minute-bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESM25,2025-01-02T09:30:00,200,202,199,201,10
                ESM25,2025-01-02T09:31:00,201,203,200,202,10
                ESH25,2025-01-02T09:32:00,102,104,101,103,10
                ESH25,2025-01-02T09:32:00,102,104,101,103,10
            """.trimIndent().toByteArray(),
            DatasetFrequency.MINUTE,
        )

        assertEquals(1, imported.rowsRejected)
        val issues = service.qualityIssues(imported.id)
        assertEquals(listOf("DUPLICATE_BAR", "MISSING_TIMESTAMP"), issues.map { it.code })
        assertEquals("ESH25", issues.last().contract)
        assertTrue(issues.last().message.contains("1 missing minute bar(s)"))
    }

    @Test
    fun `does not report gaps when timestamps are consecutive at the declared frequency`() {
        val imported = service.importDataset(
            "minute-bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:31:00,101,103,100,102,10
            """.trimIndent().toByteArray(),
            DatasetFrequency.MINUTE,
        )

        assertEquals("COMPLETED", imported.status)
        assertTrue(service.qualityIssues(imported.id).isEmpty())
    }

    @Test
    fun `rejects unsupported extensions and missing required columns`() {
        val unsupported = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.importDataset("bars.xlsx", "xlsx", "data".toByteArray())
        }
        assertEquals(HttpStatus.BAD_REQUEST, unsupported.statusCode)

        val missingColumns = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.importDataset("bars.csv", "csv", "contract,timestamp,open".toByteArray())
        }
        assertEquals(HttpStatus.BAD_REQUEST, missingColumns.statusCode)
    }

    @Test
    fun `rejects malformed Parquet bytes`() {
        val error = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.importDataset("bars.parquet", "parquet", "not parquet".toByteArray())
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `classifies CSV syntax errors as bad requests`() {
        val error = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.importDataset("bars.csv", "csv", "contract,timestamp\n\"unterminated".toByteArray())
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `rejects malformed CSV records with extra fields`() {
        val csv = """
            contract,timestamp,open,high,low,close,volume
            ESH25,2025-01-02T09:30:00,100,102,99,101,10,unexpected
        """.trimIndent()

        val imported = service.importDataset("bars.csv", "csv", csv.toByteArray())

        assertEquals(0, imported.rowsImported)
        assertEquals(1, imported.rowsRejected)
    }

    @Test
    fun `classifies out of range epoch timestamps as malformed records`() {
        val imported = service.importDataset(
            "out-of-range-timestamp.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,9223372036854775808,100,102,99,101,10
            """.trimIndent().toByteArray(),
        )

        assertEquals(0, imported.rowsImported)
        assertEquals(1, imported.rowsRejected)
        assertEquals("MALFORMED_RECORD", service.qualityIssues(imported.id).single().code)
    }

    @Test
    fun `lists imported datasets newest first`() {
        service.importDataset(
            "first.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10".toByteArray(),
        )
        service.importDataset(
            "second.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:31:00,101,103,100,102,11".toByteArray(),
        )

        val datasets = service.listDatasets()

        assertEquals(listOf("second.csv", "first.csv"), datasets.map { it.filename })
    }

    @Test
    fun `imports sample Parquet dataset`() {
        val imported = service.importDataset(
            "ESH25-daily.parquet",
            "parquet",
            readSample("ESH25-daily.parquet"),
            DatasetFrequency.DAILY,
        )

        assertEquals("COMPLETED_WITH_WARNINGS", imported.status)
        assertTrue(imported.rowsImported > 0)
        assertEquals(0, imported.rowsRejected)
        assertTrue(service.qualityIssues(imported.id).any { it.code == "MISSING_TIMESTAMP" })
        assertNotNull(imported.id)
    }

    @Test
    fun `imports sample minute Parquet dataset using Chicago wall timestamps`() {
        val imported = service.importDataset(
            "ESH25-minute.parquet",
            "parquet",
            readSample("ESH25-minute.parquet"),
            DatasetFrequency.MINUTE,
        )

        assertEquals("COMPLETED_WITH_WARNINGS", imported.status)
        assertTrue(imported.rowsImported > 0)
        assertEquals(0, imported.rowsRejected)
        assertTrue(service.qualityIssues(imported.id).any { it.code == "MISSING_TIMESTAMP" })
    }

    private fun readSample(filename: String): ByteArray =
        javaClass.getResourceAsStream("/sample-data/$filename")?.use { it.readBytes() }
            ?: error("Missing test fixture: sample-data/$filename")
}
