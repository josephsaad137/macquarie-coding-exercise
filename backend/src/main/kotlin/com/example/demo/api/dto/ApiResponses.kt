package com.example.demo.api.dto

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ImportDatasetResponse(
    val id: UUID,
    val filename: String,
    val status: String,
    val rowsImported: Int,
    val rowsRejected: Int,
    val frequency: String,
)

data class DatasetResponse(
    val id: UUID,
    val filename: String,
    val status: String,
    val rowsImported: Int,
    val rowsRejected: Int,
    val uploadedAt: Instant,
    val frequency: String,
)

data class QualityResponse(
    val datasetId: UUID,
    val totalIssues: Long,
    val page: Int,
    val size: Int,
    val issues: List<QualityIssueResponse>,
)

data class QualityIssueResponse(
    val rowNumber: Long?,
    val contract: String?,
    val timestamp: LocalDateTime?,
    val code: String,
    val message: String,
)

data class DailyBarResponse(
    val contract: String,
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

data class RollingVwapResponse(
    val contract: String,
    val timestamp: LocalDateTime,
    val value: Double,
)

data class InsightResponse(
    val code: String,
    val title: String,
    val description: String,
    val recommendation: String,
    val evidence: List<String>,
)
