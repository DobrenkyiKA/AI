package com.kdob.piq.ai.infrastructure.web.handler

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Map<String, String>> {
        logger.error("Unhandled exception: ", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (e.message ?: "Internal Server Error")))
    }
}