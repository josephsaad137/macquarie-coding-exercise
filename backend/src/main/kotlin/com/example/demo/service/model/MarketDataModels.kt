package com.example.demo.service.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class MarketDataBar(
    val contract: String,
    val timestamp: LocalDateTime,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

data class QualityReport(
    val datasetId: UUID,
    val totalIssues: Long,
    val issues: List<QualityIssue>,
)

data class QualityIssue(
    val rowNumber: Long?,
    val contract: String?,
    val timestamp: LocalDateTime?,
    val code: String,
    val message: String,
)

data class DailyBar(
    val contract: String,
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

data class RollingVwapPoint(
    val contract: String,
    val timestamp: LocalDateTime,
    val value: Double,
)

data class AnalyticsPage<T>(
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val items: List<T>,
)

data class Insight(
    val code: String,
    val title: String,
    val description: String,
    val recommendation: String,
    val evidence: List<String> = emptyList(),
)
