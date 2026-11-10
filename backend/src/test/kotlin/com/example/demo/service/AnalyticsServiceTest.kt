package com.example.demo.service

import com.example.demo.service.model.DailyBar
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AnalyticsServiceTest {
    @Test
    fun `aggregates daily OHLCV in timestamp order`() {
        val analytics = serviceWithData(
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
            analytics.dailyBars("ESH25", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-03")),
        )
    }

    @Test
    fun `filters daily bars by inclusive date range and normalized contract`() {
        val analytics = serviceWithData(
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
            analytics.dailyBars(" esh25 ", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-02")),
        )
    }

    @Test
    fun `calculates rolling 15 minute VWAP from typical prices and excludes left boundary`() {
        val analytics = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-02T09:30:00,100,102,99,101,10
                ESH25,2025-01-02T09:40:00,101,105,100,104,30
                ESH25,2025-01-02T09:45:00,104,108,103,107,10
            """.trimIndent(),
        )

        val points = analytics.rollingVwap(
            "ESH25",
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
        )

        assertEquals(3, points.size)
        assertEquals("ESH25", points[0].contract)
        assertEquals(LocalDateTime.parse("2025-01-02T09:30:00"), points[0].timestamp)
        assertClose(100.6666666667, points[0].value)
        assertClose((100.6666666667 * 10 + 103.0 * 30) / 40, points[1].value)
        assertEquals(LocalDateTime.parse("2025-01-02T09:45:00"), points[2].timestamp)
        assertClose((103.0 * 30 + 106.0 * 10) / 40, points[2].value)
    }

    @Test
    fun `rolling VWAP omits windows with no positive volume and filters contract and dates`() {
        val analytics = serviceWithData(
            """
                contract,timestamp,open,high,low,close,volume
                ESH25,2025-01-01T23:59:00,100,102,99,101,10
                ESH25,2025-01-02T00:00:00,101,103,100,102,0
                ESM25,2025-01-02T00:00:00,200,202,199,201,10
            """.trimIndent(),
        )

        val points = analytics.rollingVwap(
            "ESH25",
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
        )

        assertEquals(1, points.size)
        assertEquals(LocalDateTime.parse("2025-01-02T00:00:00"), points.single().timestamp)
        assertClose(100.6666666667, points.single().value)
    }

    @Test
    fun `returns empty results when no imported bars match`() {
        val analytics = serviceWithData(
            "contract,timestamp,open,high,low,close,volume\nESH25,2025-01-02T09:30:00,100,102,99,101,10",
        )

        assertTrue(
            analytics.dailyBars("NQH25", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-03")).isEmpty(),
        )
        assertTrue(
            analytics.rollingVwap("NQH25", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-03")).isEmpty(),
        )
    }

    private fun serviceWithData(csv: String): AnalyticsService {
        val datasets = DatasetService()
        datasets.importDataset("bars.csv", "csv", csv.toByteArray())
        return AnalyticsService(datasets)
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 1e-8, "Expected $expected but got $actual")
    }
}
