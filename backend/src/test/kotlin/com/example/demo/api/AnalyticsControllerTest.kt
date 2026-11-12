package com.example.demo.api

import com.example.demo.service.AnalyticsService
import com.example.demo.service.model.AnalyticsPage
import com.example.demo.service.model.DailyBar
import com.example.demo.service.model.RollingVwapPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AnalyticsControllerTest {
    private val analyticsService: AnalyticsService = mock()
    private lateinit var mockMvc: MockMvc
    private val datasetId = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(AnalyticsController(analyticsService)).build()
    }

    @Test
    fun `returns daily bars for contract and date range`() {
        whenever(
            analyticsService.dailyBars(
                datasetId,
                "ESH25",
                LocalDate.parse("2025-01-02"),
                LocalDate.parse("2025-01-03"),
                0,
                100,
            ),
        ).thenReturn(
            AnalyticsPage(
                totalElements = 1,
                page = 0,
                size = 100,
                rangeStart = "2025-01-02",
                rangeEnd = "2025-01-02",
                items = listOf(
                DailyBar(
                    contract = "ESH25",
                    date = LocalDate.parse("2025-01-02"),
                    open = 100.0,
                    high = 105.0,
                    low = 98.0,
                    close = 103.0,
                    volume = 42L,
                ),
                ),
            ),
        )

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("contract", "ESH25")
                .param("from", "2025-01-02")
                .param("to", "2025-01-03"),
        )
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"totalElements":1,"page":0,"size":100,"rangeStart":"2025-01-02","rangeEnd":"2025-01-02","items":[{"contract":"ESH25","date":"2025-01-02","open":100.0,"high":105.0,"low":98.0,"close":103.0,"volume":42}]}""",
                ),
            )

        verify(analyticsService).dailyBars(
            datasetId,
            "ESH25",
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-03"),
            0,
            100,
        )
    }

    @Test
    fun `rejects inverted analytics date range`() {
        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("from", "2025-01-04")
                .param("to", "2025-01-03"),
        )
            .andExpect(status().isBadRequest)

        verify(analyticsService, never()).dailyBars(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `rejects malformed analytics dates`() {
        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("from", "not-a-date")
                .param("to", "2025-01-03"),
        )
            .andExpect(status().isBadRequest)

        verify(analyticsService, never()).dailyBars(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `requires a dataset id for analytics queries`() {
        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("from", "2025-01-02")
                .param("to", "2025-01-03"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/analytics/vwap")
                .param("from", "2025-01-02")
                .param("to", "2025-01-03"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("from", "2025-01-02")
                .param("to", "2025-01-03")
                .param("page", "-1"),
        ).andExpect(status().isBadRequest)

        verify(analyticsService, never()).dailyBars(any(), any(), any(), any(), any(), any())
        verify(analyticsService, never()).rollingVwap(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `returns rolling VWAP points`() {
        whenever(
            analyticsService.rollingVwap(
                eq(datasetId),
                eq(null),
                eq(LocalDate.parse("2025-01-02")),
                eq(LocalDate.parse("2025-01-02")),
                eq(0),
                eq(100),
            ),
        ).thenReturn(
            AnalyticsPage(
                totalElements = 1,
                page = 0,
                size = 100,
                rangeStart = "2025-01-02T09:45:00",
                rangeEnd = "2025-01-02T09:45:00",
                items = listOf(
                RollingVwapPoint(
                    contract = "ESH25",
                    timestamp = LocalDateTime.parse("2025-01-02T09:45:00"),
                    value = 101.25,
                ),
                ),
            ),
        )

        mockMvc.perform(
            get("/analytics/vwap")
                .param("datasetId", datasetId.toString())
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"totalElements":1,"page":0,"size":100,"rangeStart":"2025-01-02T09:45:00","rangeEnd":"2025-01-02T09:45:00","items":[{"contract":"ESH25","timestamp":"2025-01-02T09:45:00","value":101.25}]}""",
                ),
            )

        verify(analyticsService).rollingVwap(
            datasetId,
            null,
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
            0,
            100,
        )
    }

    @Test
    fun `paginates analytics results and enforces size bounds`() {
        whenever(
            analyticsService.dailyBars(
                datasetId,
                null,
                LocalDate.parse("2025-01-02"),
                LocalDate.parse("2025-01-03"),
                1,
                20,
            ),
        ).thenReturn(AnalyticsPage(totalElements = 0, page = 1, size = 20, items = emptyList()))

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("from", "2025-01-02")
                .param("to", "2025-01-03")
                .param("page", "1")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(content().json("""{"totalElements":0,"page":1,"size":20,"items":[]}"""))

        mockMvc.perform(
            get("/analytics/vwap")
                .param("datasetId", datasetId.toString())
                .param("from", "2025-01-02")
                .param("to", "2025-01-03")
                .param("size", "501"),
        ).andExpect(status().isBadRequest)

        verify(analyticsService).dailyBars(
            datasetId,
            null,
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-03"),
            1,
            20,
        )
        verify(analyticsService, never()).rollingVwap(any(), any(), any(), any(), any(), any())
    }
}
