package com.example.demo.api

import java.util.UUID
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
class DatasetApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `imports real daily sample and exposes it through dataset quality and insights APIs`() {
        val file = MockMultipartFile(
            "file",
            "ESH25-daily.parquet",
            "application/octet-stream",
            readSample("ESH25-daily.parquet"),
        )
        val importResponse = mockMvc.perform(
            multipart("/datasets").file(file).param("frequency", "daily"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("ESH25-daily.parquet"))
            .andExpect(jsonPath("$.rowsImported").isNumber)
            .andExpect(jsonPath("$.frequency").value("daily"))
            .andReturn()
            .response
            .contentAsString
        val datasetId = extractDatasetId(importResponse)

        mockMvc.perform(get("/datasets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$datasetId')].filename").value("ESH25-daily.parquet"))
            .andExpect(jsonPath("$[?(@.id == '$datasetId')].frequency").value("daily"))

        mockMvc.perform(get("/datasets/$datasetId/quality").param("page", "0").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.datasetId").value(datasetId.toString()))
            .andExpect(jsonPath("$.totalIssues").isNumber)
            .andExpect(jsonPath("$.totalIssues").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.issues[0].code").value("MISSING_TIMESTAMP"))
            .andExpect(jsonPath("$.issues[0].rowNumber").doesNotExist())

        mockMvc.perform(get("/insights").param("datasetId", datasetId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value("RECURRING_MISSING_TIMESTAMP"))
            .andExpect(jsonPath("$[0].recommendation").isNotEmpty)
    }

    private fun readSample(filename: String): ByteArray =
        javaClass.getResourceAsStream("/sample-data/$filename")?.use { it.readBytes() }
            ?: error("Missing test fixture: sample-data/$filename")

    private fun extractDatasetId(response: String): UUID =
        UUID.fromString(
            Regex(""""id":"([^"]+)"""").find(response)?.groupValues?.get(1)
                ?: error("Import response did not contain a dataset ID"),
        )
}
