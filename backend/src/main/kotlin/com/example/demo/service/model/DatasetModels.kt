package com.example.demo.service.model

import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

enum class DatasetFrequency {
    MINUTE,
    DAILY,
}

data class DatasetImportResult(
    val id: UUID,
    val filename: String,
    val status: String,
    val rowsImported: Int,
    val rowsRejected: Int,
    val frequency: DatasetFrequency = DatasetFrequency.MINUTE,
)

data class DatasetSummary(
    val id: UUID,
    val filename: String,
    val status: String,
    val rowsImported: Int,
    val rowsRejected: Int,
    val uploadedAt: Instant,
    val frequency: DatasetFrequency = DatasetFrequency.MINUTE,
)

data class BarKey(val contract: String, val timestamp: LocalDateTime)

sealed interface BarParseResult {
    data class Valid(val bar: MarketDataBar) : BarParseResult
    data class Invalid(val code: String, val message: String) : BarParseResult
}

data class ImportedRows(val bars: List<MarketDataBar>, val issues: List<QualityIssue>, val rowsRejected: Int)

data class TabularData(
    val headers: List<String>,
    val rows: Sequence<List<String>>,
)

