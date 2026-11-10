package com.example.demo.service

import com.example.demo.persistence.InMemoryDatasetRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InsightsServiceTest {
    @Test
    fun `suggests recurring rules for repeated quality issue patterns`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:31:00,100,99,98,101,10
                ESH25,2025-01-02T09:32:00,100,99,98,101,10
                ESM25,2025-01-02T09:30:00,200,202,199,201,-1
                ESM25,2025-01-02T09:31:00,200,202,199,201,-1
            """.trimIndent().toByteArray(),
        )

        val insights = InsightsService(repository).getInsights(imported.id)

        assertEquals(
            setOf("RECURRING_DUPLICATE_BAR", "RECURRING_INVALID_OHLC", "RECURRING_INVALID_VOLUME"),
            insights.map { it.code }.toSet(),
        )
        val duplicateInsight = insights.single { it.code == "RECURRING_DUPLICATE_BAR" }
        assertTrue(duplicateInsight.recommendation.isNotBlank())
        assertTrue(duplicateInsight.evidence.any { it.contains("2") })
        assertTrue(insights.all { it.title.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun `provides a general recommendation for isolated quality issues`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,-1
            """.trimIndent().toByteArray(),
        )

        val insights = InsightsService(repository).getInsights(imported.id)

        assertEquals(1, insights.size)
        assertEquals("DATA_QUALITY_ISSUES", insights.single().code)
        assertTrue(insights.single().evidence.any { it.contains("1") })
    }

    @Test
    fun `recommends repairing recurring gaps using the uploaded dataset frequency`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "minute-bars.csv",
            "csv",
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:32:00,101,103,100,102,10
                ESH25,2025-01-02T09:34:00,102,104,101,103,10
            """.trimIndent().toByteArray(),
        )

        val insight = InsightsService(repository).getInsights(imported.id).single()

        assertEquals("RECURRING_MISSING_TIMESTAMP", insight.code)
        assertEquals("Recurring timestamp gaps", insight.title)
        assertTrue(insight.recommendation.contains("minute dataset"))
    }

    @Test
    fun `returns no insights for a clean dataset`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val imported = datasets.importDataset(
            "bars.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10".toByteArray(),
        )

        assertTrue(InsightsService(repository).getInsights(imported.id).isEmpty())
    }

    @Test
    fun `rejects unknown dataset ids`() {
        val error = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            InsightsService(InMemoryDatasetRepository()).getInsights(UUID.randomUUID())
        }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }
}
