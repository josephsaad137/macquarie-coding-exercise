package com.example.demo.api

import com.example.demo.service.AnalyticsApiService
import com.example.demo.service.DailyBar
import com.example.demo.service.RollingVwapPoint
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val analyticsApiService: AnalyticsApiService = mock()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(AnalyticsController(analyticsApiService)).build()
    }

    @Test
    fun `returns daily bars for contract and date range`() {
        whenever(
            analyticsApiService.dailyBars(
                "ESH25",
                LocalDate.parse("2025-01-02"),
                LocalDate.parse("2025-01-03"),
            ),
        ).thenReturn(
            listOf(
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
        )

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("contract", "ESH25")
                .param("from", "2025-01-02")
                .param("to", "2025-01-03"),
        )
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """[{"contract":"ESH25","date":"2025-01-02","open":100.0,"high":105.0,"low":98.0,"close":103.0,"volume":42}]""",
                ),
            )

        verify(analyticsApiService).dailyBars(
            "ESH25",
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-03"),
        )
    }

    @Test
    fun `rejects inverted analytics date range`() {
        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("from", "2025-01-04")
                .param("to", "2025-01-03"),
        )
            .andExpect(status().isBadRequest)

        verify(analyticsApiService, never()).dailyBars(any(), any(), any())
    }

    @Test
    fun `rejects malformed analytics dates`() {
        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("from", "not-a-date")
                .param("to", "2025-01-03"),
        )
            .andExpect(status().isBadRequest)

        verify(analyticsApiService, never()).dailyBars(any(), any(), any())
    }

    @Test
    fun `returns rolling VWAP points`() {
        whenever(
            analyticsApiService.rollingVwap(
                eq(null),
                eq(LocalDate.parse("2025-01-02")),
                eq(LocalDate.parse("2025-01-02")),
            ),
        ).thenReturn(
            listOf(
                RollingVwapPoint(
                    contract = "ESH25",
                    timestamp = LocalDateTime.parse("2025-01-02T09:45:00"),
                    value = 101.25,
                ),
            ),
        )

        mockMvc.perform(
            get("/analytics/vwap")
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """[{"contract":"ESH25","timestamp":"2025-01-02T09:45:00","value":101.25}]""",
                ),
            )

        verify(analyticsApiService).rollingVwap(
            null,
            LocalDate.parse("2025-01-02"),
            LocalDate.parse("2025-01-02"),
        )
    }
}
