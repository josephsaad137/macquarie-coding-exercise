package com.example.demo.api

import com.example.demo.api.dto.DailyBarResponse
import com.example.demo.api.dto.RollingVwapResponse
import com.example.demo.service.AnalyticsService
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/analytics")
class AnalyticsController(
    private val analyticsService: AnalyticsService,
) {
    @GetMapping("/daily-bars")
    fun dailyBars(
        @RequestParam datasetId: UUID,
        @RequestParam(required = false) contract: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<DailyBarResponse> {
        validateDateRange(from, to)
        val normalizedContract = normalizeContract(contract)
        return analyticsService.dailyBars(datasetId, normalizedContract, from, to).map { bar ->
            DailyBarResponse(
                contract = bar.contract,
                date = bar.date,
                open = bar.open,
                high = bar.high,
                low = bar.low,
                close = bar.close,
                volume = bar.volume,
            )
        }
    }

    @GetMapping("/vwap")
    fun rollingVwap(
        @RequestParam datasetId: UUID,
        @RequestParam(required = false) contract: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<RollingVwapResponse> {
        validateDateRange(from, to)
        val normalizedContract = normalizeContract(contract)
        return analyticsService.rollingVwap(datasetId, normalizedContract, from, to).map { point ->
            RollingVwapResponse(
                contract = point.contract,
                timestamp = point.timestamp,
                value = point.value,
            )
        }
    }

    private fun validateDateRange(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'")
        }
    }

    private fun normalizeContract(contract: String?): String? {
        if (contract == null) return null
        val normalized = contract.trim()
        if (normalized.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract must not be blank")
        }
        return normalized.uppercase(Locale.ROOT)
    }
}
