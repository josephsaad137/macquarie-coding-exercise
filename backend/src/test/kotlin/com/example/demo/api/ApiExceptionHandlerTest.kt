package com.example.demo.api

import com.example.demo.exception.InvalidRequestException
import com.example.demo.exception.PayloadTooLargeException
import com.example.demo.exception.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.server.ResponseStatusException
import org.springframework.mock.web.MockHttpServletRequest

class ApiExceptionHandlerTest {
    private val handler = ApiExceptionHandler()

    @Test
    fun `preserves status and body for expected HTTP errors`() {
        val request = request()
        val exception = ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request")

        val response = handler.handleException(exception, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `maps application exceptions to their HTTP statuses`() {
        assertEquals(
            HttpStatus.BAD_REQUEST,
            handler.handleApiException(InvalidRequestException("Invalid input")).statusCode,
        )
        assertEquals(
            HttpStatus.NOT_FOUND,
            handler.handleApiException(ResourceNotFoundException("Missing resource")).statusCode,
        )
        val tooLarge = handler.handleApiException(PayloadTooLargeException("Upload too large"))
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, tooLarge.statusCode)
        assertEquals("Upload too large", tooLarge.body?.detail)
    }

    @Test
    fun `logs and returns a generic internal error for unexpected exceptions`() {
        val request = request()

        val response = handler.handleException(IllegalStateException("internal detail"), request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("An unexpected error occurred", (response.body as ProblemDetail).detail)
    }

    private fun request(): HttpServletRequest = MockHttpServletRequest().apply {
        method = "GET"
        requestURI = "/datasets"
    }
}
