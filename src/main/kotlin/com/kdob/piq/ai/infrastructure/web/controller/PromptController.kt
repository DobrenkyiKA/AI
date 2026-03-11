package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.prompt.PromptService
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/prompts")
class PromptController(
    private val promptService: PromptService
) {

    @GetMapping
    fun getAllPrompts(@RequestParam(required = false) type: PromptType?): List<PromptResponse> {
        return if (type != null) {
            promptService.findAllByType(type)
        } else {
            promptService.findAll()
        }
    }

    @GetMapping("/{name}")
    fun getPromptByName(@PathVariable name: String): PromptResponse? {
        return promptService.findByName(name)
    }

    @PostMapping
    fun createPrompt(@RequestBody request: CreatePromptRequest): PromptResponse {
        return promptService.createPrompt(request)
    }

    @PutMapping("/{name}")
    fun updatePrompt(@PathVariable name: String, @RequestBody request: UpdatePromptRequest): PromptResponse {
        return promptService.updatePrompt(name, request)
    }

    @DeleteMapping("/{name}")
    fun deletePrompt(@PathVariable name: String) {
        promptService.deletePrompt(name)
    }
}
