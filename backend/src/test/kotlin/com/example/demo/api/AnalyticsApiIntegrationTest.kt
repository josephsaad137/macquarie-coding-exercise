package com.example.demo.api

import com.example.demo.persistence.DatasetRepository
import java.time.LocalDate
import java.util.UUID
import org.hamcrest.Matchers.greaterThan
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
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var datasetRepository: DatasetRepository

    @Test
    fun `calculates paged analytics from the real minute sample`() {
        val file = MockMultipartFile(
            "file",
            "ESH25-minute.parquet",
            "application/octet-stream",
            readSample("ESH25-minute.parquet"),
        )
        val importResponse = mockMvc.perform(
            multipart("/datasets").file(file).param("frequency", "minute"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("ESH25-minute.parquet"))
            .andExpect(jsonPath("$.frequency").value("minute"))
            .andExpect(jsonPath("$.rowsImported").value(greaterThan(0)))
            .andReturn()
            .response
            .contentAsString
        val datasetId = extractDatasetId(importResponse)
        val bars = datasetRepository.findMarketData(datasetId)
        assertTrue(bars.isNotEmpty())
        val minDate = bars.minOf { it.timestamp.toLocalDate() }
        val maxDate = bars.maxOf { it.timestamp.toLocalDate() }
        val from = minDate.toString()
        val to = maxDate.toString()
        assertTrue(
            minDate >= LocalDate.of(2000, 1, 1) && maxDate <= LocalDate.of(2100, 1, 1),
            "$minDate through $maxDate",
        )

        mockMvc.perform(
            get("/analytics/daily-bars")
                .param("datasetId", datasetId.toString())
                .param("from", from)
                .param("to", to)
                .param("page", "0")
                .param("size", "2"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(greaterThan(2)))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].contract").isNotEmpty)
            .andExpect(jsonPath("$.items[0].date").isNotEmpty)
            .andExpect(jsonPath("$.items[0].volume").isNumber)

        mockMvc.perform(
            get("/analytics/vwap")
                .param("datasetId", datasetId.toString())
                .param("from", from)
                .param("to", to)
                .param("page", "0")
                .param("size", "2"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(greaterThan(2)))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].contract").isNotEmpty)
            .andExpect(jsonPath("$.items[0].timestamp").isNotEmpty)
            .andExpect(jsonPath("$.items[0].value").value(greaterThan(0.0)))
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
