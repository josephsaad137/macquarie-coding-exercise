package com.example.demo.api

import com.example.demo.api.dto.InsightResponse
import com.example.demo.service.InsightsApiService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/insights")
class InsightsController(
    private val insightsApiService: InsightsApiService,
) {
    @GetMapping
    fun getInsights(@RequestParam datasetId: UUID): List<InsightResponse> =
        insightsApiService.getInsights(datasetId).map { insight ->
            InsightResponse(
                code = insight.code,
                title = insight.title,
                description = insight.description,
                recommendation = insight.recommendation,
                evidence = insight.evidence,
            )
        }
}
