package com.kdob.piq.ai.infrastructure.web.facade

import com.kdob.piq.ai.application.service.prompt.PromptService
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import com.kdob.piq.ai.infrastructure.web.mapper.PromptMapper.toResponse
import org.springframework.stereotype.Component

@Component
class PromptFacade(
    private val promptService: PromptService
) {
    fun getAll() = promptService.findAll().map { it.toResponse() }
    fun getByType(type: PromptType): List<PromptResponse> = promptService.findAllByType(type).map { it.toResponse() }
    fun get(name: String): PromptResponse = promptService.get(name).toResponse()
    fun create(request: CreatePromptRequest): PromptResponse = promptService.create(request).toResponse()
    fun update(request: UpdatePromptRequest): PromptResponse =
        promptService.update(request.name, request.content).toResponse()
    fun delete(name: String) = promptService.delete(name)
}
