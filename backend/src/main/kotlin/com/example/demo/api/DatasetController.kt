package com.example.demo.api

import com.example.demo.api.dto.DatasetResponse
import com.example.demo.api.dto.ImportDatasetResponse
import com.example.demo.api.dto.QualityIssueResponse
import com.example.demo.api.dto.QualityResponse
import com.example.demo.service.DatasetService
import com.example.demo.service.QualityService
import java.util.Locale
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/datasets")
class DatasetController(
    private val datasetService: DatasetService,
    private val qualityService: QualityService,
) {

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("csv", "parquet")
        const val MAX_UPLOAD_SIZE_BYTES = 50L * 1024 * 1024
        const val MAX_PAGE_SIZE = 500
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importDataset(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportDatasetResponse> {
        val originalFilename = file.originalFilename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf(String::isNotBlank)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A filename is required")
        val extension = originalFilename.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_EXTENSIONS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only CSV and Parquet files are supported",
            )
        }
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty")
        }
        if (file.size > MAX_UPLOAD_SIZE_BYTES) {
            throw ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE,
                "The uploaded file exceeds the file size limit",
            )
        }

        val result = datasetService.importDataset(originalFilename, extension, file.bytes)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ImportDatasetResponse(
                id = result.id,
                filename = result.filename,
                status = result.status,
                rowsImported = result.rowsImported,
                rowsRejected = result.rowsRejected,
            ),
        )
    }

    @GetMapping
    fun listDatasets(): List<DatasetResponse> = datasetService.listDatasets().map { dataset ->
        DatasetResponse(
            id = dataset.id,
            filename = dataset.filename,
            status = dataset.status,
            rowsImported = dataset.rowsImported,
            rowsRejected = dataset.rowsRejected,
            uploadedAt = dataset.uploadedAt,
        )
    }

    @GetMapping("/{datasetId}/quality")
    fun getQuality(
        @PathVariable datasetId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): QualityResponse {
        if (page < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater")
        }
        if (size !in 1..MAX_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and $MAX_PAGE_SIZE")
        }

        val report = qualityService.getQuality(datasetId, page, size)
        return QualityResponse(
            datasetId = report.datasetId,
            totalIssues = report.totalIssues,
            page = page,
            size = size,
            issues = report.issues.map { issue ->
                QualityIssueResponse(
                    rowNumber = issue.rowNumber,
                    contract = issue.contract,
                    timestamp = issue.timestamp,
                    code = issue.code,
                    message = issue.message,
                )
            },
        )
    }
}
