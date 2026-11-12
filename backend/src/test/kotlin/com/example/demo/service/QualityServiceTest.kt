package com.example.demo.service

import com.example.demo.persistence.InMemoryDatasetRepository
import com.example.demo.exception.InvalidRequestException
import com.example.demo.exception.ResourceNotFoundException
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class QualityServiceTest {
    @Test
    fun `returns all import quality findings with dataset id and total count`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:31:00,100,99,98,101,10
                ESM25,2025-01-02T09:32:00,200,202,199,201,-1
                ESH25,not-a-timestamp,100,102,99,101,10
            """.trimIndent().toByteArray(),
        )

        val report = QualityService(repository).getQuality(imported.id, page = 0, size = 10)

        assertEquals(imported.id, report.datasetId)
        assertEquals(4L, report.totalIssues)
        assertEquals(
            listOf("DUPLICATE_BAR", "INVALID_OHLC", "INVALID_VOLUME", "MALFORMED_RECORD"),
            report.issues.map { it.code },
        )
        assertEquals(4L, report.issues[1].rowNumber)
        assertEquals("ESM25", report.issues[2].contract)
        assertEquals(null, report.issues[3].timestamp)
    }

    @Test
    fun `paginates issues in source row order while retaining total`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,99,98,101,10
                ESH25,2025-01-02T09:31:00,100,99,98,101,10
                ESH25,2025-01-02T09:32:00,100,99,98,101,10
                ESH25,2025-01-02T09:33:00,100,99,98,101,10
                ESH25,2025-01-02T09:34:00,100,99,98,101,10
            """.trimIndent().toByteArray(),
        )

        val secondPage = QualityService(repository).getQuality(imported.id, page = 1, size = 2)

        assertEquals(5L, secondPage.totalIssues)
        assertEquals(listOf(4L, 5L), secondPage.issues.map { it.rowNumber })
    }

    @Test
    fun `returns an empty page when offset is beyond issue count`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,99,98,101,10
            """.trimIndent().toByteArray(),
        )

        val report = QualityService(repository).getQuality(imported.id, page = Int.MAX_VALUE, size = 500)

        assertEquals(1L, report.totalIssues)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `returns empty issue page for a clean dataset`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "clean.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10".toByteArray(),
        )

        val report = QualityService(repository).getQuality(imported.id, page = 0, size = 25)

        assertEquals(0L, report.totalIssues)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `rejects unknown dataset id`() {
        val error = org.junit.jupiter.api.assertThrows<ResourceNotFoundException> {
            QualityService(InMemoryDatasetRepository()).getQuality(UUID.randomUUID(), page = 0, size = 25)
        }

        assertTrue(error.message!!.contains("was not found"))
    }

    @Test
    fun `rejects invalid pagination`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "clean.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10".toByteArray(),
        )
        val service = QualityService(repository)

        val negativePage = org.junit.jupiter.api.assertThrows<InvalidRequestException> {
            service.getQuality(imported.id, page = -1, size = 10)
        }
        val invalidSize = org.junit.jupiter.api.assertThrows<InvalidRequestException> {
            service.getQuality(imported.id, page = 0, size = 0)
        }
        val excessiveSize = org.junit.jupiter.api.assertThrows<InvalidRequestException> {
            service.getQuality(imported.id, page = 0, size = 501)
        }

        assertTrue(negativePage.message!!.contains("Page"))
        assertTrue(invalidSize.message!!.contains("Size"))
        assertTrue(excessiveSize.message!!.contains("Size"))
    }

    @Test
    fun `uses nullable issue timestamps without changing their values`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,99,98,101,10".toByteArray(),
        )

        val report = QualityService(repository).getQuality(imported.id, page = 0, size = 10)

        assertEquals(LocalDateTime.parse("2025-01-02T09:30:00"), report.issues.single().timestamp)
    }
}
