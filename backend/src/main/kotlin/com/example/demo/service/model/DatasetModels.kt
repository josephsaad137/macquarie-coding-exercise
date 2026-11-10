package com.example.demo.service.model

import java.time.Instant
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
