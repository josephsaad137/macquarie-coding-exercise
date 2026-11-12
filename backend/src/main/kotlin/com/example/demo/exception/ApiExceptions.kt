package com.example.demo.exception

sealed class ApiException(message: String) : RuntimeException(message)

class InvalidRequestException(message: String) : ApiException(message)

class ResourceNotFoundException(message: String) : ApiException(message)

class PayloadTooLargeException(message: String) : ApiException(message)
