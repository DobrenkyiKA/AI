package com.kdob.piq.ai.infrastructure.web.controller.web.handler

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
}

class GlobalExceptionHandlerTest {

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(ExceptionTestController())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun `should handle all exceptions and return 500`() {
        mockMvc.perform(get("/test-exception"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("Test exception message"))
    }
}
