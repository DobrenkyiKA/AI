package com.kdob.piq.ai.infrastructure.web.controller.web.handler

import com.kdob.piq.ai.domain.exception.ResourceNotFoundException
import com.kdob.piq.ai.infrastructure.web.handler.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
private class ExceptionTestController {
    @GetMapping("/test-exception")
    fun throwException() {
        throw RuntimeException("Test exception message")
    }

    @GetMapping("/test-not-found")
    fun throwNotFound() {
        throw ResourceNotFoundException("Not found")
    }

    @GetMapping("/test-illegal-argument")
    fun throwIllegalArgument() {
        throw IllegalArgumentException("Invalid argument")
    }
}

class GlobalExceptionHandlerTest {

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(ExceptionTestController())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun `should handle ResourceNotFoundException and return 404`() {
        mockMvc.perform(get("/test-not-found"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Not found"))
    }

    @Test
    fun `should handle IllegalArgumentException and return 400`() {
        mockMvc.perform(get("/test-illegal-argument"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid argument"))
    }

    @Test
    fun `should handle all exceptions and return 500`() {
        mockMvc.perform(get("/test-exception"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Test exception message"))
    }
}
