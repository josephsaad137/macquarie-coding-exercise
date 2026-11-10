package com.example.demo.service

import com.example.demo.persistence.InMemoryDatasetRepository
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus

class DatasetServiceTest {
    private val service = DatasetService(InMemoryDatasetRepository())

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
        val path = Path.of(
            "..",
            "historical-futures-data-sample",
            "data",
            "daily",
            "CME",
            "ES",
            "ESH25.parquet",
        )
        assertTrue(Files.exists(path), "Expected sample Parquet file at $path")

        val imported = service.importDataset(path.fileName.toString(), "parquet", Files.readAllBytes(path))

        assertEquals("COMPLETED", imported.status)
        assertTrue(imported.rowsImported > 0)
        assertEquals(0, imported.rowsRejected)
        assertNotNull(imported.id)
    }

    @Test
    fun `imports sample minute Parquet dataset using Chicago wall timestamps`() {
        val path = Path.of(
            "..",
            "historical-futures-data-sample",
            "data",
            "minute",
            "CME",
            "ES",
            "ESH25.parquet",
        )
        assertTrue(Files.exists(path), "Expected sample Parquet file at $path")

        val imported = service.importDataset(path.fileName.toString(), "parquet", Files.readAllBytes(path))

        assertEquals("COMPLETED", imported.status)
        assertTrue(imported.rowsImported > 0)
        assertEquals(0, imported.rowsRejected)
    }
}
