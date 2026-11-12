package com.example.demo.service

import com.example.demo.persistence.DatasetRepository
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.service.model.DatasetFrequency
import com.example.demo.service.model.Insight
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class InsightsService(
    private val datasetRepository: DatasetRepository,
) {
    fun getInsights(datasetId: UUID): List<Insight> {
        val dataset = datasetRepository.findDataset(datasetId)
            ?: throw ResourceNotFoundException("Dataset $datasetId was not found")
        val issues = datasetRepository.findQualityIssues(datasetId)
        if (issues.isEmpty()) return emptyList()

        val recurringIssues = issues.groupBy { it.code }
            .filterValues { it.size >= MIN_RECURRING_OCCURRENCES }
        val recurringInsights = recurringIssues.toSortedMap().map { (code, matches) ->
            recurringInsight(code, matches.size, dataset.filename, dataset.frequency)
        }
        val isolatedIssueCount = issues.count { it.code !in recurringIssues.keys }
        val isolatedInsights = if (isolatedIssueCount > 0) {
            listOf(
                Insight(
                    code = "DATA_QUALITY_ISSUES",
                    title = "Data quality issues detected",
                    description = "$isolatedIssueCount isolated data quality finding(s) were found in ${dataset.filename}.",
                    recommendation = "Review the affected records and validate required fields, OHLC relationships, non-negative volume, and expected timestamps.",
                    evidence = listOf("$isolatedIssueCount finding(s) require review."),
                ),
            )
        } else {
            emptyList()
        }

        return recurringInsights + isolatedInsights
    }

    private fun recurringInsight(
        code: String,
        count: Int,
        filename: String,
        frequency: DatasetFrequency,
    ): Insight {
        val guidance = when (code) {
            "DUPLICATE_BAR" -> "Deduplicate records by contract and timestamp before ingestion."
            "CONFLICTING_BAR" -> "Resolve conflicting values for each contract and timestamp using the authoritative source record."
            "INVALID_OHLC" -> "Validate that high is at least open, close, and low, and low is at most open, close, and high."
            "INVALID_VOLUME" -> "Require volume to be a non-negative integer before publishing the file."
            "MISSING_TIMESTAMP" -> "Restore missing bars or verify that the ${frequency.name.lowercase()} dataset has complete timestamp coverage."
            else -> "Validate the source records against the required market-data schema before ingestion."
        }
        val title = when (code) {
            "DUPLICATE_BAR" -> "Recurring duplicate bars"
            "CONFLICTING_BAR" -> "Conflicting bars"
            "INVALID_OHLC" -> "Recurring invalid OHLC values"
            "INVALID_VOLUME" -> "Recurring invalid volumes"
            "MISSING_TIMESTAMP" -> "Recurring timestamp gaps"
            else -> "Recurring malformed records"
        }

        return Insight(
            code = "RECURRING_$code",
            title = title,
            description = "$count occurrences of $code were found in $filename.",
            recommendation = guidance,
            evidence = listOf("$count finding(s) were recorded for $code."),
        )
    }

    private companion object {
        const val MIN_RECURRING_OCCURRENCES = 2
    }
}
