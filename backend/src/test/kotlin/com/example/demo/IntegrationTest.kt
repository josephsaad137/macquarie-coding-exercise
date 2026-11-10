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
            $contract,2025-01-02T09:40:00,101,105,100,104,30
            $contract,2025-01-02T09:40:00,101,105,100,104,30
            $contract,2025-01-02T09:50:00,104,103,100,102,5
        """.trimIndent()
        val file = MockMultipartFile("file", "integration-bars.csv", "text/csv", csv.toByteArray())

        val importResponse = mockMvc.perform(multipart("/datasets").file(file))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("integration-bars.csv"))
            .andExpect(jsonPath("$.status").value("COMPLETED_WITH_WARNINGS"))
            .andExpect(jsonPath("$.rowsImported").value(2))
            .andExpect(jsonPath("$.rowsRejected").value(2))
            .andReturn()
            .response
            .contentAsString
        val datasetId = UUID.fromString(
            Regex(""""id":"([^"]+)"""").find(importResponse)?.groupValues?.get(1)
                ?: error("Import response did not contain a dataset ID"),
        )

        mockMvc.perform(get("/datasets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$datasetId')].filename").value("integration-bars.csv"))

        mockMvc.perform(get("/datasets/$datasetId/quality").param("page", "0").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.datasetId").value(datasetId.toString()))
            .andExpect(jsonPath("$.totalIssues").value(2))
            .andExpect(jsonPath("$.issues[0].code").value("DUPLICATE_BAR"))
            .andExpect(jsonPath("$.issues[1].code").value("INVALID_OHLC"))

        mockMvc.perform(get("/insights").param("datasetId", datasetId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value("DATA_QUALITY_ISSUES"))
            .andExpect(jsonPath("$[0].evidence[0]").value("2 rejected row(s) require review."))

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("contract", contract.lowercase())
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].contract").value(normalizedContract))
            .andExpect(jsonPath("$[0].open").value(100.0))
            .andExpect(jsonPath("$[0].high").value(105.0))
            .andExpect(jsonPath("$[0].low").value(99.0))
            .andExpect(jsonPath("$[0].close").value(104.0))
            .andExpect(jsonPath("$[0].volume").value(40))

        mockMvc.perform(
            get("/analytics/vwap")
                .param("contract", contract)
                .param("from", "2025-01-02")
                .param("to", "2025-01-02"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].value").value(closeTo(102.4166666667, 0.000001)))
    }
}
