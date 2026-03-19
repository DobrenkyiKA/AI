package com.kdob.piq.ai.application.service.prompt

import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PromptService(
    private val promptRepository: PromptRepository,
    private val promptSyncService: PromptSyncService
) {

    @Transactional(readOnly = true)
    fun findAll(): List<PromptResponse> {
        return promptRepository.findAll().map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findAllByType(type: PromptType): List<PromptResponse> {
        return promptRepository.findAllByType(type).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun get(name: String): PromptResponse? {
        return promptRepository.findByName(name)?.toResponse()
    }

    @Transactional
    fun create(request: CreatePromptRequest): PromptResponse {
        val existing = promptRepository.findByName(request.name)
        if (existing != null) {
            throw IllegalArgumentException("Prompt with name '${request.name}' already exists")
        }
        val prompt = PromptEntity(
            type = request.type,
            name = request.name,
            content = request.content
        )
        val saved = promptRepository.save(prompt).toResponse()
        promptSyncService.exportToNewVersion("Auto-export after creating prompt: ${request.name}")
        return saved
    }

    @Transactional
    fun update(name: String, request: UpdatePromptRequest): PromptResponse {
        val prompt = promptRepository.findByName(name)
            ?: throw IllegalArgumentException("Prompt with name '$name' not found")
        
        request.name?.let { 
            if (it != prompt.name && promptRepository.findByName(it) != null) {
                throw IllegalArgumentException("Prompt with name '$it' already exists")
            }
            prompt.name = it 
        }
        request.content?.let { prompt.content = it }
        
        val saved = promptRepository.save(prompt).toResponse()
        promptSyncService.exportToNewVersion("Auto-export after updating prompt: $name")
        return saved
    }

    @Transactional
    fun delete(name: String) {
        promptRepository.deleteByName(name)
        promptSyncService.exportToNewVersion("Auto-export after deleting prompt: $name")
    }

    private fun PromptEntity.toResponse() = PromptResponse(
        id = this.id,
        type = this.type,
        name = this.name,
        content = this.content
    )
}
