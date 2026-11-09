package com.example.demo.service

interface DatasetApiService {
    fun importDataset(filename: String, contentType: String?, content: ByteArray): DatasetImportResult

    fun listDatasets(): List<DatasetSummary>
}
