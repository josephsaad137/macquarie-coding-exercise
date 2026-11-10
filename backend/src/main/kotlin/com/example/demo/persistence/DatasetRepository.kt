package com.example.demo.persistence

import com.example.demo.service.model.DatasetSummary
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.QualityIssue
import java.util.UUID

data class StoredDataset(
    val summary: DatasetSummary,
    val bars: List<MarketDataBar>,
    val qualityIssues: List<QualityIssue>,
)

interface DatasetRepository {
    fun save(dataset: StoredDataset)

    fun findDataset(datasetId: UUID): DatasetSummary?

    fun findAllDatasets(): List<DatasetSummary>

    fun findMarketData(datasetId: UUID): List<MarketDataBar>

    fun findAllMarketData(): List<MarketDataBar>

    fun findQualityIssues(datasetId: UUID): List<QualityIssue>
}
