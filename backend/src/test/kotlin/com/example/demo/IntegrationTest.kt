package com.example.demo

import java.util.UUID
import org.hamcrest.Matchers.closeTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class IntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `imports dataset and serves persisted records through quality insights and analytics APIs`() {
        val contract = "IT${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val normalizedContract = contract.uppercase()
        val csv = """
            contract,timestamp,open,high,low,close,volume
            $contract,2025-01-02T09:30:00,100,102,99,101,10
            $contract,2025-01-02T09:33:00,101,105,100,104,30
            $contract,2025-01-02T09:33:00,101,105,100,104,30
            $contract,2025-01-02T09:33:00,101,106,100,105,30
            $contract,2025-01-02T09:34:00,104,103,100,102,5
        """.trimIndent()
        val file = MockMultipartFile("file", "integration-bars.csv", "text/csv", csv.toByteArray())

        val importResponse = mockMvc.perform(multipart("/datasets").file(file).param("frequency", "minute"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("integration-bars.csv"))
            .andExpect(jsonPath("$.status").value("COMPLETED_WITH_WARNINGS"))
            .andExpect(jsonPath("$.rowsImported").value(2))
            .andExpect(jsonPath("$.rowsRejected").value(3))
            .andExpect(jsonPath("$.frequency").value("minute"))
            .andReturn()
            .response
            .contentAsString
        val datasetId = UUID.fromString(
            Regex(""""id":"([^"]+)"""").find(importResponse)?.groupValues?.get(1)
                ?: error("Import response did not contain a dataset ID"),
        )
        val secondFile = MockMultipartFile(
            "file",
            "other-bars.csv",
            "text/csv",
            """
                contract,timestamp,open,high,low,close,volume
                $contract,2025-01-02T09:30:00,200,202,199,201,2
                $contract,2025-01-02T09:31:00,201,203,200,202,2
            """.trimIndent().toByteArray(),
        )
        val secondImportResponse = mockMvc.perform(
            multipart("/datasets").file(secondFile).param("frequency", "minute"),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        val secondDatasetId = UUID.fromString(
            Regex(""""id":"([^"]+)"""").find(secondImportResponse)?.groupValues?.get(1)
                ?: error("Second import response did not contain a dataset ID"),
        )

        mockMvc.perform(get("/datasets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$datasetId')].filename").value("integration-bars.csv"))

        mockMvc.perform(get("/datasets/$datasetId/quality").param("page", "0").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.datasetId").value(datasetId.toString()))
            .andExpect(jsonPath("$.totalIssues").value(4))
            .andExpect(jsonPath("$.issues[0].code").value("DUPLICATE_BAR"))
            .andExpect(jsonPath("$.issues[1].code").value("CONFLICTING_BAR"))
            .andExpect(jsonPath("$.issues[2].code").value("INVALID_OHLC"))
            .andExpect(jsonPath("$.issues[3].code").value("MISSING_TIMESTAMP"))
            .andExpect(jsonPath("$.issues[3].rowNumber").doesNotExist())
            .andExpect(jsonPath("$.issues[3].timestamp").value("2025-01-02T09:31:00"))

        mockMvc.perform(get("/insights").param("datasetId", datasetId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value("DATA_QUALITY_ISSUES"))
            .andExpect(jsonPath("$[0].evidence[0]").value("4 finding(s) require review."))

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("page", "0")
                .param("size", "100")
                .param("contract", contract.lowercase())
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].contract").value(normalizedContract))
            .andExpect(jsonPath("$.items[0].open").value(100.0))
            .andExpect(jsonPath("$.items[0].high").value(105.0))
            .andExpect(jsonPath("$.items[0].low").value(99.0))
            .andExpect(jsonPath("$.items[0].close").value(104.0))
            .andExpect(jsonPath("$.items[0].volume").value(40))

        mockMvc.perform(
            get("/analytics/vwap")
                .param("datasetId", datasetId.toString())
                .param("page", "0")
                .param("size", "100")
                .param("contract", contract)
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[1].value").value(closeTo(102.4166666667, 0.000001)))

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", secondDatasetId.toString())
                .param("page", "0")
                .param("size", "100")
                .param("contract", contract)
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].volume").value(4))
            .andExpect(jsonPath("$.items[0].open").value(200.0))

        mockMvc.perform(
            get("/analytics/vwap")
                .param("datasetId", secondDatasetId.toString())
                .param("page", "0")
                .param("size", "100")
                .param("contract", contract)
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].value").value(closeTo(200.6666666667, 0.000001)))
    }
}
