package com.example.demo.api

import com.example.demo.service.Insight
import com.example.demo.service.InsightsApiService
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class InsightsControllerTest {
    private val insightsApiService: InsightsApiService = mock()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(InsightsController(insightsApiService)).build()
    }

    @Test
    fun `returns insights for a dataset`() {
        val datasetId = UUID.fromString("6b8ebdb2-b60f-49a7-afcc-66f12cb37db3")
        whenever(insightsApiService.getInsights(datasetId)).thenReturn(
            listOf(
                Insight(
                    code = "RECURRING_GAPS",
                    title = "Recurring timestamp gaps",
                    description = "Gaps are recurring for ESH25",
                    recommendation = "Review the source feed interval",
                ),
            ),
        )

        mockMvc.perform(get("/insights").param("datasetId", datasetId.toString()))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """[{"code":"RECURRING_GAPS","title":"Recurring timestamp gaps","description":"Gaps are recurring for ESH25","recommendation":"Review the source feed interval","evidence":[]} ]""",
                ),
            )

        verify(insightsApiService).getInsights(datasetId)
    }
}
