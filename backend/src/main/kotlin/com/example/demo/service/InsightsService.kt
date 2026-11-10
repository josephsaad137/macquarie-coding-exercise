package com.example.demo.service

import com.example.demo.service.model.Insight
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class InsightsService(
    private val datasetService: DatasetService,
) {
    fun getInsights(datasetId: UUID): List<Insight> {
        val dataset = datasetService.findDataset(datasetId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset $datasetId was not found")
        val issues = datasetService.qualityIssues(datasetId)
        if (issues.isEmpty()) return emptyList()

        val recurringIssues = issues.groupBy { it.code }
            .filterValues { it.size >= MIN_RECURRING_OCCURRENCES }
        val recurringInsights = recurringIssues.toSortedMap().map { (code, matches) ->
            recurringInsight(code, matches.size, dataset.filename)
        }
        val isolatedIssueCount = issues.count { it.code !in recurringIssues.keys }
        val isolatedInsights = if (isolatedIssueCount > 0) {
            listOf(
                Insight(
                    code = "DATA_QUALITY_ISSUES",
                    title = "Data quality issues detected",
                    description = "$isolatedIssueCount isolated invalid or duplicate records were found in ${dataset.filename}.",
                    recommendation = "Review the affected rows and add source-side validation for required fields, OHLC relationships, and non-negative volume.",
                    evidence = listOf("$isolatedIssueCount rejected row(s) require review."),
                ),
            )
        } else {
            emptyList()
        }

        return recurringInsights + isolatedInsights
    }

    private fun recurringInsight(code: String, count: Int, filename: String): Insight {
        val guidance = when (code) {
            "DUPLICATE_BAR" -> "Deduplicate records by contract and timestamp before ingestion."
            "INVALID_OHLC" -> "Validate that high is at least open, close, and low, and low is at most open, close, and high."
            "INVALID_VOLUME" -> "Require volume to be a non-negative integer before publishing the file."
            else -> "Validate the source records against the required market-data schema before ingestion."
        }
        val title = when (code) {
            "DUPLICATE_BAR" -> "Recurring duplicate bars"
            "INVALID_OHLC" -> "Recurring invalid OHLC values"
            "INVALID_VOLUME" -> "Recurring invalid volumes"
            else -> "Recurring malformed records"
        }
        return Insight(
            code = "RECURRING_$code",
            title = title,
            description = "$count rows in $filename share the $code quality issue.",
            recommendation = guidance,
            evidence = listOf("$count rows were rejected for $code."),
        )
    }

    private companion object {
        const val MIN_RECURRING_OCCURRENCES = 2
    }
}
