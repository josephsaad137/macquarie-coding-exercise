package com.example.demo.api

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.ProblemDetail

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        if (exception is ErrorResponse) {
            if (exception.statusCode.is5xxServerError) {
                logger.error(
                    "Request failed method={} uri={} status={}",
                    request.method,
                    request.requestURI,
                    exception.statusCode.value(),
                    exception,
                )
            }
            return ResponseEntity(exception.body, exception.headers, exception.statusCode)
        }

        logger.error(
            "Unhandled request failure method={} uri={}",
            request.method,
            request.requestURI,
            exception,
        )
        val body = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
