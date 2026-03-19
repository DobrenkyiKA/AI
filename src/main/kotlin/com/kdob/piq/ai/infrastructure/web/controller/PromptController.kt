package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.prompt.PromptService
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/prompts")
class PromptController(
    private val promptService: PromptService
) {

    @GetMapping
    fun getOfType(@RequestParam type: PromptType): List<PromptResponse> = promptService.findAllByType(type)

    @GetMapping
    fun getAll(): List<PromptResponse> = promptService.findAll()


    @GetMapping("/{name}")
    fun getPromptByName(@PathVariable name: String): PromptResponse? {
        return promptService.get(name)
    }

    @PostMapping
    fun createPrompt(@RequestBody request: CreatePromptRequest): PromptResponse {
        return promptService.create(request)
    }

    @PutMapping("/{name}")
    fun updatePrompt(@PathVariable name: String, @RequestBody request: UpdatePromptRequest): PromptResponse {
        return promptService.update(name, request)
    }

    @DeleteMapping("/{name}")
    fun deletePrompt(@PathVariable name: String) {
        promptService.delete(name)
    }
}