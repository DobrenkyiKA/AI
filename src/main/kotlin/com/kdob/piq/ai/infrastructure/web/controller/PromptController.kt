package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import com.kdob.piq.ai.infrastructure.web.facade.PromptFacade
import jakarta.validation.Valid
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

    @PostMapping
    fun createPrompt(@Valid @RequestBody request: CreatePromptRequest): PromptResponse = promptFacade.create(request)

    @GetMapping("/{name}")
    fun getPromptByName(@PathVariable name: String): PromptResponse? = promptFacade.get(name)

    @PutMapping("/{name}")
    fun updatePrompt(@PathVariable name: String, @Valid @RequestBody request: UpdatePromptRequest): PromptResponse {
        return promptFacade.update(name, request)
    }

    @DeleteMapping("/{name}")
    fun deletePrompt(@PathVariable name: String) = promptFacade.delete(name)
}