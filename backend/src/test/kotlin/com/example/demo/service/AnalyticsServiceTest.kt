package com.example.demo.service

import com.example.demo.persistence.InMemoryDatasetRepository
import com.example.demo.service.model.DailyBar
import java.util.UUID
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AnalyticsServiceTest {
    @Test
    fun `aggregates daily OHLCV in timestamp order`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:32:00,101,105,100,104,30
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:31:00,100.5,103,100,102,20
                ESM25,2025-01-02T09:30:00,200,202,199,201,5
                ESH25,2025-01-03T09:30:00,104,106,103,105,12
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                DailyBar("ESH25", LocalDate.parse("2025-01-02"), 100.0, 105.0, 99.0, 104.0, 60),
                DailyBar("ESH25", LocalDate.parse("2025-01-03"), 104.0, 106.0, 103.0, 105.0, 12),
            ),
            analytics.dailyBars(datasetId, "ESH25", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-03"), page = 0, size = 10).items,
        )
    }

    @Test
    fun `filters daily bars by inclusive date range and normalized contract`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-01T23:59:00,100,102,99,101,10
                ESH25,2025-01-02T00:00:00,101,103,100,102,20
                ESH25,2025-01-03T00:00:00,102,104,101,103,30
                ESM25,2025-01-02T00:00:00,200,202,199,201,40
            """.trimIndent(),
        )

        assertEquals(
            listOf(DailyBar("ESH25", LocalDate.parse("2025-01-02"), 101.0, 103.0, 100.0, 102.0, 20)),
            analytics.dailyBars(datasetId, " esh25 ", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), page = 0, size = 10).items,
        )
    }

    @Test
    fun `calculates rolling 15 minute VWAP from typical prices and excludes left boundary`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:40:00,101,105,100,104,30
                ESH25,2025-01-02T09:45:00,104,108,103,107,10
            """.trimIndent(),
        )

        val points = analytics.rollingVwap(
            datasetId,
            "ESH25",
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
            page = 0,
            size = 10,
        )

        assertEquals(3, points.items.size)
        assertEquals("ESH25", points.items[0].contract)
        assertEquals(LocalDateTime.parse("2025-01-02T09:30:00"), points.items[0].timestamp)
        assertClose(100.6666666667, points.items[0].value)
        assertClose((100.6666666667 * 10 + 103.0 * 30) / 40, points.items[1].value)
        assertEquals(LocalDateTime.parse("2025-01-02T09:45:00"), points.items[2].timestamp)
        assertClose((103.0 * 30 + 106.0 * 10) / 40, points.items[2].value)
    }

    @Test
    fun `rolling VWAP omits windows with no positive volume and filters contract and dates`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-01T23:59:00,100,102,99,101,10
                ESH25,2025-01-02T00:00:00,101,103,100,102,0
                ESM25,2025-01-02T00:00:00,200,202,199,201,10
            """.trimIndent(),
        )

        val points = analytics.rollingVwap(
            datasetId,
            "ESH25",
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
            page = 0,
            size = 10,
        )

        assertEquals(1, points.items.size)
        assertEquals(LocalDateTime.parse("2025-01-02T00:00:00"), points.items.single().timestamp)
        assertClose(100.6666666667, points.items.single().value)
    }

    @Test
    fun `returns empty results when no imported bars match`() {
        val (analytics, datasetId) = serviceWithData(
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10",
        )

        assertTrue(
            analytics.dailyBars(datasetId, "NQH25", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-03"), 0, 10).items.isEmpty(),
        )
        assertTrue(
            analytics.rollingVwap(datasetId, "NQH25", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-03"), 0, 10).items.isEmpty(),
        )
    }

    @Test
    fun `keeps analytics isolated to the selected dataset`() {
        val repository = InMemoryDatasetRepository()
        val datasets = DatasetService(repository)
        val first = datasets.importDataset(
            "first.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10".toByteArray(),
        )
        val second = datasets.importDataset(
            "second.csv",
            "csv",
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,200,202,199,201,40".toByteArray(),
        )
        val analytics = AnalyticsService(repository)

        assertEquals(10, analytics.dailyBars(first.id, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 0, 10).items.single().volume)
        assertEquals(40, analytics.dailyBars(second.id, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 0, 10).items.single().volume)
        assertEquals(1, analytics.rollingVwap(first.id, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 0, 10).items.size)
        assertEquals(1, analytics.rollingVwap(second.id, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 0, 10).items.size)
    }

    @Test
    fun `paginates both analytics result types and returns total counts`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:31:00,101,103,100,102,10
                ESH25,2025-01-02T09:32:00,102,104,101,103,10
                ESM25,2025-01-02T09:30:00,200,202,199,201,10
            """.trimIndent(),
        )

        val dailyPage = analytics.dailyBars(datasetId, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 1, 1)
        val vwapPage = analytics.rollingVwap(datasetId, "ESH25", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 1, 1)

        assertEquals(2L, dailyPage.totalElements)
        assertEquals(1, dailyPage.items.size)
        assertEquals("ESM25", dailyPage.items.single().contract)
        assertEquals(3L, vwapPage.totalElements)
        assertEquals(1, vwapPage.items.size)
        assertEquals(LocalDateTime.parse("2025-01-02T09:31:00"), vwapPage.items.single().timestamp)

        val pastEnd = analytics.dailyBars(
            datasetId,
            null,
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
            page = 10,
            size = 1,
        )
        assertEquals(2L, pastEnd.totalElements)
        assertTrue(pastEnd.items.isEmpty())
    }

    @Test
    fun `orders analytics chronologically across contracts for recent chart windows`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-03T09:30:00,100,102,99,101,10
                ESM25,2025-01-01T09:30:00,200,202,199,201,10
                ESH25,2025-01-02T09:30:00,101,103,100,102,10
                ESM25,2025-01-02T09:30:00,201,203,200,202,10
            """.trimIndent(),
        )

        val dailyBars = analytics.dailyBars(
            datasetId, null, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-03"), page = 0, size = 10,
        )
        val vwapPoints = analytics.rollingVwap(
            datasetId, null, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-03"), page = 0, size = 10,
        )

        assertEquals(
            listOf(
                "2025-01-01:ESM25",
                "2025-01-02:ESH25",
                "2025-01-02:ESM25",
                "2025-01-03:ESH25",
            ),
            dailyBars.items.map { "${it.date}:${it.contract}" },
        )
        assertEquals(
            listOf(
                "2025-01-01T09:30:ESM25",
                "2025-01-02T09:30:ESH25",
                "2025-01-02T09:30:ESM25",
                "2025-01-03T09:30:ESH25",
            ),
            vwapPoints.items.map { "${it.timestamp}:${it.contract}" },
        )
    }

    @Test
    fun `returns full analytics ranges across contracts independently of the requested page`() {
        val (analytics, datasetId) = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                AAA,2025-01-03T09:30:00,100,102,99,101,10
                BBB,2025-01-01T09:30:00,100,102,99,101,10
            """.trimIndent(),
        )

        val dailyPage = analytics.dailyBars(
            datasetId,
            null,
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-01-03"),
            page = 1,
            size = 1,
        )
        val vwapPage = analytics.rollingVwap(
            datasetId,
            null,
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-01-03"),
            page = 1,
            size = 1,
        )

        assertEquals("2025-01-01", dailyPage.rangeStart)
        assertEquals("2025-01-03", dailyPage.rangeEnd)
        assertEquals("2025-01-01T09:30", vwapPage.rangeStart)
        assertEquals("2025-01-03T09:30", vwapPage.rangeEnd)
    }

    @Test
    fun `rejects invalid analytics pagination`() {
        val (analytics, datasetId) = serviceWithData(
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10",
        )

        val negativePage = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            analytics.dailyBars(datasetId, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), -1, 10)
        }
        val excessiveSize = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            analytics.rollingVwap(datasetId, null, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02"), 0, 501)
        }

        assertEquals(HttpStatus.BAD_REQUEST, negativePage.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, excessiveSize.statusCode)
    }

    @Test
    fun `rejects analytics requests for unknown datasets`() {
        val analytics = AnalyticsService(InMemoryDatasetRepository())

        val dailyError = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            analytics.dailyBars(UUID.randomUUID(), null, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-01"), 0, 10)
        }
        val vwapError = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            analytics.rollingVwap(UUID.randomUUID(), null, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-01"), 0, 10)
        }

        assertEquals(HttpStatus.NOT_FOUND, dailyError.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, vwapError.statusCode)
    }

    private fun serviceWithData(csv: String): Pair<AnalyticsService, UUID> {
        val repository = InMemoryDatasetRepository()
        val dataset = DatasetService(repository).importDataset("bars.csv", "csv", csv.toByteArray())
        return AnalyticsService(repository) to dataset.id
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 1e-8, "Expected $expected but got $actual")
    }
}
