package com.example.demo.service

import com.example.demo.service.model.DailyBar
import com.example.demo.service.model.Insight
import com.example.demo.service.model.QualityReport
import com.example.demo.service.model.RollingVwapPoint
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class QualityService {
    fun getQuality(datasetId: UUID, page: Int, size: Int): QualityReport = notImplemented()
}

@Service
class AnalyticsService {
    fun dailyBars(contract: String?, from: LocalDate, to: LocalDate): List<DailyBar> = notImplemented()
    fun rollingVwap(contract: String?, from: LocalDate, to: LocalDate): List<RollingVwapPoint> = notImplemented()
}

@Service
class InsightsService {
    fun getInsights(datasetId: UUID): List<Insight> = notImplemented()
}

private fun <T> notImplemented(): T =
    throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Market data processing is not implemented yet")
