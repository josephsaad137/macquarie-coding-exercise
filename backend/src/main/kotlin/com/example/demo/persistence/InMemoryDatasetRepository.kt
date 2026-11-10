package com.example.demo.persistence

import com.example.demo.service.model.DatasetSummary
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.QualityIssue
import java.util.Collections
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class InMemoryDatasetRepository : DatasetRepository {
    private val lock = Any()
    private val datasets = linkedMapOf<UUID, StoredDataset>()

    override fun save(dataset: StoredDataset) {
        val snapshot = dataset.copy(
            bars = immutableCopy(dataset.bars),
            qualityIssues = immutableCopy(dataset.qualityIssues),
        )
        synchronized(lock) {
            datasets[dataset.summary.id] = snapshot
        }
    }

    override fun findDataset(datasetId: UUID): DatasetSummary? = synchronized(lock) {
        datasets[datasetId]?.summary
    }

    override fun findAllDatasets(): List<DatasetSummary> = synchronized(lock) {
        immutableCopy(datasets.values.map(StoredDataset::summary).sortedByDescending(DatasetSummary::uploadedAt))
    }

    override fun findMarketData(datasetId: UUID): List<MarketDataBar> = synchronized(lock) {
        datasets[datasetId]?.bars?.let(::immutableCopy) ?: emptyList()
    }

    override fun findAllMarketData(): List<MarketDataBar> = synchronized(lock) {
        immutableCopy(datasets.values.flatMap(StoredDataset::bars))
    }

    override fun findQualityIssues(datasetId: UUID): List<QualityIssue> = synchronized(lock) {
        datasets[datasetId]?.qualityIssues?.let(::immutableCopy) ?: emptyList()
    }

    private fun <T> immutableCopy(values: Collection<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}
