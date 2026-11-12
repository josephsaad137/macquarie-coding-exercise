package com.example.demo.api

import com.example.demo.api.dto.DailyBarResponse
import com.example.demo.api.dto.DailyBarsPageResponse
import com.example.demo.api.dto.RollingVwapPageResponse
import com.example.demo.api.dto.RollingVwapResponse
import com.example.demo.exception.InvalidRequestException
import com.example.demo.service.AnalyticsService
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/analytics")
class AnalyticsController(
    private val analyticsService: AnalyticsService,
) {
    private companion object {
        const val MAX_PAGE_SIZE = 500
    }

    @GetMapping("/daily-bars")
    fun dailyBars(
        @RequestParam datasetId: UUID,
        @RequestParam(required = false) contract: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): DailyBarsPageResponse {
        validateDateRange(from, to)
        validatePagination(page, size)
        val normalizedContract = normalizeContract(contract)
        val result = analyticsService.dailyBars(datasetId, normalizedContract, from, to, page, size)
        return DailyBarsPageResponse(
            totalElements = result.totalElements,
            page = result.page,
            size = result.size,
            rangeStart = result.rangeStart,
            rangeEnd = result.rangeEnd,
            items = result.items.map { bar ->
                DailyBarResponse(
                    contract = bar.contract,
                    date = bar.date,
                    open = bar.open,
                    high = bar.high,
                    low = bar.low,
                    close = bar.close,
                    volume = bar.volume,
                )
            },
        )
    }

    @GetMapping("/vwap")
    fun rollingVwap(
        @RequestParam datasetId: UUID,
        @RequestParam(required = false) contract: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): RollingVwapPageResponse {
        validateDateRange(from, to)
        validatePagination(page, size)
        val normalizedContract = normalizeContract(contract)
        val result = analyticsService.rollingVwap(datasetId, normalizedContract, from, to, page, size)
        return RollingVwapPageResponse(
            totalElements = result.totalElements,
            page = result.page,
            size = result.size,
            rangeStart = result.rangeStart,
            rangeEnd = result.rangeEnd,
            items = result.items.map { point ->
                RollingVwapResponse(
                    contract = point.contract,
                    timestamp = point.timestamp,
                    value = point.value,
                )
            },
        )
    }

    private fun validateDateRange(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) {
            throw InvalidRequestException("'to' must be on or after 'from'")
        }
    }

    private fun validatePagination(page: Int, size: Int) {
        if (page < 0) {
            throw InvalidRequestException("Page must be zero or greater")
        }
        if (size !in 1..MAX_PAGE_SIZE) {
            throw InvalidRequestException("Size must be between 1 and $MAX_PAGE_SIZE")
        }
    }

    private fun normalizeContract(contract: String?): String? {
        if (contract == null) return null
        val normalized = contract.trim()
        if (normalized.isEmpty()) {
            throw InvalidRequestException("Contract must not be blank")
        }
        return normalized.uppercase(Locale.ROOT)
    }
}
