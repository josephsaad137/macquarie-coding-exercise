package com.example.demo.service

import com.example.demo.persistence.DatasetRepository
import com.example.demo.service.model.QualityReport
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class QualityService(
    private val datasetRepository: DatasetRepository,
) {
    fun getQuality(datasetId: UUID, page: Int, size: Int): QualityReport {
        if (page < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater")
        }
        if (size !in 1..MAX_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and $MAX_PAGE_SIZE")
        }
        if (datasetRepository.findDataset(datasetId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset $datasetId was not found")
        }

        val allIssues = datasetRepository.findQualityIssues(datasetId).sortedBy { it.rowNumber }
        val offset = page.toLong() * size
        val issues = if (offset >= allIssues.size) {
            emptyList()
        } else {
            allIssues.drop(offset.toInt()).take(size)
        }

        return QualityReport(
            datasetId = datasetId,
            totalIssues = allIssues.size.toLong(),
            issues = issues,
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 500
    }
}
