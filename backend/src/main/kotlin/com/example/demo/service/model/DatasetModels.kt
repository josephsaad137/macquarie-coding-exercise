package com.example.demo.service.model

import java.time.Instant
import java.util.UUID

data class DatasetImportResult(
    val id: UUID,
    val filename: String,
    val status: String,
    val rowsImported: Int,
    val rowsRejected: Int,
)

data class DatasetSummary(
    val id: UUID,
    val filename: String,
    val status: String,
    val rowsImported: Int,
    val rowsRejected: Int,
    val uploadedAt: Instant,
)
