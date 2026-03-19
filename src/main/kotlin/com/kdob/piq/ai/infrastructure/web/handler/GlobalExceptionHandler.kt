package com.kdob.piq.ai.infrastructure.web.handler

import com.kdob.piq.ai.domain.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(e: ResourceNotFoundException): ResponseEntity<Map<String, String>> {
        val message = e.message ?: "Resource not found"
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to message, "message" to message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        val message = e.message ?: "Invalid request"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to message, "message" to message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Map<String, String>> {
        logger.error("Unhandled exception: ", e)
        val message = e.message ?: "Internal Server Error"
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to message, "message" to message))
    }
}