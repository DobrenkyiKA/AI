package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import com.kdob.piq.ai.infrastructure.web.facade.PromptFacade
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/prompts")
class PromptController(
    private val promptFacade: PromptFacade
) {
    @GetMapping(params = ["type"])
    fun getOfType(@RequestParam type: PromptType): List<PromptResponse> = promptFacade.getByType(type)

    @GetMapping(params = ["!type"])
    fun getAll(): List<PromptResponse> = promptFacade.getAll()

    @GetMapping("/{name}")
    fun getPromptByName(@PathVariable name: String): PromptResponse? {
        return promptFacade.get(name)
    }

    @PostMapping
    fun createPrompt(@RequestBody request: CreatePromptRequest): PromptResponse {
        return promptFacade.create(request)
    }

    @PutMapping("/{name}")
    fun updatePrompt(@PathVariable name: String, @RequestBody request: UpdatePromptRequest): PromptResponse {
        return promptFacade.update(name, request)
    }

    @DeleteMapping("/{name}")
    fun deletePrompt(@PathVariable name: String) {
        promptFacade.delete(name)
    }
}