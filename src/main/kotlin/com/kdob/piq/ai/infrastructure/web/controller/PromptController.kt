package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import com.kdob.piq.ai.infrastructure.web.facade.PromptFacade
import com.kdob.piq.ai.infrastructure.web.validation.PromptName
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/prompts")
@Validated
class PromptController(
    private val promptFacade: PromptFacade
) {
    @GetMapping(params = ["type"])
    fun getOfType(@RequestParam type: PromptType): List<PromptResponse> = promptFacade.getByType(type)

    @GetMapping(params = ["!type"])
    fun getAll(): List<PromptResponse> = promptFacade.getAll()

    @PostMapping
    fun createPrompt(@Valid @RequestBody request: CreatePromptRequest): ResponseEntity<PromptResponse> {
        val created = promptFacade.create(request)
        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{name}")
            .buildAndExpand(created.name)
            .toUri()
        return ResponseEntity.created(location).body(created)
    }

    @GetMapping("/{name}")
    fun getPromptByName(@PathVariable @PromptName name: String): PromptResponse = promptFacade.get(name)

    @PutMapping
    fun updatePrompt(@Valid @RequestBody request: UpdatePromptRequest): PromptResponse = promptFacade.update(request)

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePrompt(@PathVariable @PromptName name: String) = promptFacade.delete(name)
}