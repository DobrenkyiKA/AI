package com.kdob.piq.ai.api.controller

import com.kdob.piq.ai.application.service.AiClientService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController(val aiService: AiClientService) {

    @GetMapping("/ai/test")
    fun test(@RequestParam topic: String) = aiService.testConnection(topic)

}