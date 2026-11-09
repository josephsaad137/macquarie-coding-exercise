package com.example.demo.api

import com.example.demo.service.DatasetApiService
import com.example.demo.service.DatasetImportResult
import com.example.demo.service.DatasetSummary
import com.example.demo.service.QualityApiService
import com.example.demo.service.QualityIssue
import com.example.demo.service.QualityReport
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DatasetControllerTest {
    private val datasetApiService: DatasetApiService = mock()
    private val qualityApiService: QualityApiService = mock()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(DatasetController(datasetApiService, qualityApiService)).build()
    }

    @Test
    fun `uploads CSV and returns import summary`() {
        val response = DatasetImportResult(
            id = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3"),
            filename = "bars.csv",
            status = "COMPLETED",
            rowsImported = 2,
            rowsRejected = 1,
        )
        whenever(datasetApiService.importDataset(eq("bars.csv"), any(), any())).thenReturn(response)
        val file = MockMultipartFile(
            "file",
            "bars.csv",
            "text/csv",
            "contract,timestamp,open,high,low,close,volume".toByteArray(),
        )

        mockMvc.perform(multipart("/datasets").file(file))
            .andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json(
                    """{"id":"6b8ebdb2-b60f-49a7-afcc-66f12cb37db3","filename":"bars.csv","status":"COMPLETED","rowsImported":2,"rowsRejected":1}""",
                ),
            )

        verify(datasetApiService).importDataset(eq("bars.csv"), eq("text/csv"), any())
    }

    @Test
    fun `accepts Parquet uploads`() {
        val response = DatasetImportResult(
            id = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3"),
            filename = "ESH25.parquet",
            status = "COMPLETED",
            rowsImported = 2,
            rowsRejected = 0,
        )
        whenever(datasetApiService.importDataset(eq("ESH25.parquet"), any(), any())).thenReturn(response)
        val file = MockMultipartFile("file", "ESH25.parquet", "application/octet-stream", byteArrayOf(1, 2, 3))

        mockMvc.perform(multipart("/datasets").file(file))
            .andExpect(status().isCreated)

        verify(datasetApiService).importDataset(eq("ESH25.parquet"), eq("application/octet-stream"), any())
    }

    @Test
    fun `rejects unsupported file extensions without calling service`() {
        val file = MockMultipartFile("file", "bars.xlsx", "application/octet-stream", byteArrayOf(1))

        mockMvc.perform(multipart("/datasets").file(file))
            .andExpect(status().isBadRequest)

        verify(datasetApiService, never()).importDataset(any(), any(), any())
    }

    @Test
    fun `rejects empty files without calling service`() {
        val file = MockMultipartFile("file", "bars.csv", "text/csv", byteArrayOf())

        mockMvc.perform(multipart("/datasets").file(file))
            .andExpect(status().isBadRequest)

        verify(datasetApiService, never()).importDataset(any(), any(), any())
    }

    @Test
    fun `lists imported datasets`() {
        val response = listOf(
            DatasetSummary(
                id = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3"),
                filename = "bars.csv",
                status = "COMPLETED",
                rowsImported = 2,
                rowsRejected = 1,
                uploadedAt = Instant.parse("2026-11-09T05:00:00Z"),
            ),
        )
        whenever(datasetApiService.listDatasets()).thenReturn(response)

        mockMvc.perform(get("/datasets"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """[{"id":"6b8ebdb2-b60f-49a7-afcc-66f12cb37db3","filename":"bars.csv","status":"COMPLETED","rowsImported":2,"rowsRejected":1,"uploadedAt":"2026-11-09T05:00:00Z"}]""",
                ),
            )

        verify(datasetApiService).listDatasets()
    }

    @Test
    fun `returns paginated quality issues`() {
        val datasetId = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3")
        whenever(qualityApiService.getQuality(datasetId, 1, 25)).thenReturn(
            QualityReport(
                datasetId = datasetId,
                totalIssues = 1,
                issues = listOf(
                    QualityIssue(
                        rowNumber = 4,
                        contract = "ESH25",
                        timestamp = LocalDateTime.parse("2025-01-02T09:31:00"),
                        code = "INVALID_OHLC",
                        message = "High is below low",
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/datasets/$datasetId/quality")
                .param("page", "1")
                .param("size", "25"),
        )
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"datasetId":"$datasetId","totalIssues":1,"page":1,"size":25,"issues":[{"rowNumber":4,"contract":"ESH25","timestamp":"2025-01-02T09:31:00","code":"INVALID_OHLC","message":"High is below low"}]}""",
                ),
            )

        verify(qualityApiService).getQuality(datasetId, 1, 25)
    }

    @Test
    fun `rejects invalid quality pagination without calling service`() {
        val datasetId = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3")

        mockMvc.perform(get("/datasets/$datasetId/quality").param("page", "-1"))
            .andExpect(status().isBadRequest)

        mockMvc.perform(get("/datasets/$datasetId/quality").param("size", "501"))
            .andExpect(status().isBadRequest)

        verify(qualityApiService, never()).getQuality(any(), any(), any())
    }
}
