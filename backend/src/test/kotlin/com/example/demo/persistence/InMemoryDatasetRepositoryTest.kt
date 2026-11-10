package com.example.demo.persistence

import com.example.demo.service.model.DatasetSummary
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.QualityIssue
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class InMemoryDatasetRepositoryTest {
    private val repository: DatasetRepository = InMemoryDatasetRepository()

    @Test
    fun `saves and retrieves a dataset with its records and quality findings`() {
        val id = UUID.randomUUID()
        val dataset = dataset(
            id = id,
            filename = "bars.csv",
            uploadedAt = Instant.parse("2025-01-02T00:00:00Z"),
            bars = listOf(bar("ESH25")),
            issues = listOf(
                QualityIssue(
                    rowNumber = 3,
                    contract = "ESH25",
                    timestamp = LocalDateTime.parse("2025-01-02T09:31:00"),
                    code = "INVALID_OHLC",
                    message = "High is below low",
                ),
            ),
        )

        repository.save(dataset)

        assertEquals(dataset.summary, repository.findDataset(id))
        assertEquals(listOf(bar("ESH25")), repository.findMarketData(id))
        assertEquals(dataset.qualityIssues, repository.findQualityIssues(id))
    }

    @Test
    fun `lists summaries newest first and does not expose mutable store state`() {
        val bars = mutableListOf(bar("ESH25"))
        val older = dataset(
            id = UUID.randomUUID(),
            filename = "older.csv",
            uploadedAt = Instant.parse("2025-01-01T00:00:00Z"),
            bars = bars,
        )
        val newer = dataset(
            id = UUID.randomUUID(),
            filename = "newer.csv",
            uploadedAt = Instant.parse("2025-01-02T00:00:00Z"),
            bars = emptyList(),
        )
        repository.save(older)
        repository.save(newer)
        bars.clear()

        val summaries = repository.findAllDatasets()

        assertEquals(listOf("newer.csv", "older.csv"), summaries.map { it.filename })
        assertNotNull(repository.findDataset(older.summary.id))
        assertEquals(listOf(bar("ESH25")), repository.findMarketData(older.summary.id))
        assertFailsWith<UnsupportedOperationException> {
            (repository.findMarketData(older.summary.id) as MutableList).clear()
        }
    }

    @Test
    fun `keeps bars and issues isolated by dataset`() {
        val first = dataset(
            id = UUID.randomUUID(),
            filename = "first.csv",
            uploadedAt = Instant.parse("2025-01-01T00:00:00Z"),
            bars = listOf(bar("ESH25")),
            issues = listOf(QualityIssue(2, "ESH25", null, "INVALID_VOLUME", "Negative volume")),
        )
        val second = dataset(
            id = UUID.randomUUID(),
            filename = "second.csv",
            uploadedAt = Instant.parse("2025-01-02T00:00:00Z"),
            bars = listOf(bar("ESM25")),
        )
        repository.save(first)
        repository.save(second)

        assertEquals(listOf(bar("ESH25")), repository.findMarketData(first.summary.id))
        assertEquals(listOf(bar("ESM25")), repository.findMarketData(second.summary.id))
        assertEquals(first.qualityIssues, repository.findQualityIssues(first.summary.id))
        assertEquals(emptyList(), repository.findQualityIssues(second.summary.id))
    }

    @Test
    fun `returns missing dataset as null and empty child collections`() {
        val unknownId = UUID.randomUUID()

        assertNull(repository.findDataset(unknownId))
        assertEquals(emptyList(), repository.findMarketData(unknownId))
        assertEquals(emptyList(), repository.findQualityIssues(unknownId))
    }

    private fun dataset(
        id: UUID,
        filename: String,
        uploadedAt: Instant,
        bars: List<MarketDataBar>,
        issues: List<QualityIssue> = emptyList(),
    ) = StoredDataset(
        summary = DatasetSummary(
            id = id,
            filename = filename,
            status = if (issues.isEmpty()) "COMPLETED" else "COMPLETED_WITH_WARNINGS",
            rowsImported = bars.size,
            rowsRejected = issues.size,
            uploadedAt = uploadedAt,
        ),
        bars = bars,
        qualityIssues = issues,
    )

    private fun bar(contract: String) = MarketDataBar(
        contract = contract,
        timestamp = LocalDateTime.parse("2025-01-02T09:30:00"),
        open = 100.0,
        high = 102.0,
        low = 99.0,
        close = 101.0,
        volume = 10,
    )
}
