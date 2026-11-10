package com.example.demo.service

import com.example.demo.service.model.QualityReport
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class QualityService {
    fun getQuality(datasetId: java.util.UUID, page: Int, size: Int): QualityReport = notImplemented()
}

private fun <T> notImplemented(): T =
    throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Market data processing is not implemented yet")
