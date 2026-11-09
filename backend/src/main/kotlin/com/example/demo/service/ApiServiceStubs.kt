package com.example.demo.service

import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class DatasetApiServiceStub : DatasetApiService {
    override fun importDataset(
        filename: String,
        contentType: String?,
        content: ByteArray,
    ): DatasetImportResult = notImplemented()

    override fun listDatasets(): List<DatasetSummary> = notImplemented()
}

@Service
class QualityApiServiceStub : QualityApiService {
    override fun getQuality(datasetId: UUID, page: Int, size: Int): QualityReport = notImplemented()
}

@Service
class AnalyticsApiServiceStub : AnalyticsApiService {
    override fun dailyBars(contract: String?, from: LocalDate, to: LocalDate): List<DailyBar> = notImplemented()

    override fun rollingVwap(
        contract: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<RollingVwapPoint> = notImplemented()
}

@Service
class InsightsApiServiceStub : InsightsApiService {
    override fun getInsights(datasetId: UUID): List<Insight> = notImplemented()
}

private fun <T> notImplemented(): T =
    throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Market data processing is not implemented yet")
