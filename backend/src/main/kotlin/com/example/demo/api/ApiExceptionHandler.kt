package com.example.demo.api

import com.example.demo.exception.ApiException
import com.example.demo.exception.InvalidRequestException
import com.example.demo.exception.PayloadTooLargeException
import com.example.demo.exception.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.beans.TypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ResponseEntity<ProblemDetail> {
        val status = when (exception) {
            is InvalidRequestException -> HttpStatus.BAD_REQUEST
            is ResourceNotFoundException -> HttpStatus.NOT_FOUND
            is PayloadTooLargeException -> HttpStatus.CONTENT_TOO_LARGE
        }
        val body = ProblemDetail.forStatusAndDetail(status, exception.message ?: status.reasonPhrase)
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        if (exception is TypeMismatchException) {
            val body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A request parameter has an invalid value")
            return ResponseEntity.badRequest().body(body)
        }

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
