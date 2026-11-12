package com.example.demo.api

import jakarta.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
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
    fun `logs and returns a generic internal error for unexpected exceptions`() {
        val request = request()

        val response = handler.handleException(IllegalStateException("internal detail"), request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("An unexpected error occurred", (response.body as org.springframework.http.ProblemDetail).detail)
    }

    private fun request(): HttpServletRequest = MockHttpServletRequest().apply {
        method = "GET"
        requestURI = "/datasets"
    }
}
