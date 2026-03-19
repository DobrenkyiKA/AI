package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.exception.ResourceNotFoundException
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PromptService(
    private val promptRepository: PromptRepository,
    private val promptSyncService: PromptSyncService
) {
    @Transactional(readOnly = true)
    fun findAll(): List<PromptEntity> {
        return promptRepository.findAll()
    }

    @Transactional(readOnly = true)
    fun findAllByType(type: PromptType): List<PromptEntity> {
        return promptRepository.findAllByType(type)
    }

    @Transactional(readOnly = true)
    fun get(name: String): PromptEntity = getPromptEntity(name)

    private fun getPromptEntity(name: String): PromptEntity =
        promptRepository.findByName(name) ?: throw ResourceNotFoundException("Prompt not found: $name")

    @Transactional
    fun create(name: String, content: String, type: PromptType): PromptEntity {
        val existing = promptRepository.findByName(name)
        if (existing != null) {
            throw IllegalArgumentException("Prompt with name '${name}' already exists")
        }
        val prompt = PromptEntity(
            type = type,
            name = name,
            content = content
        )
        val saved = promptRepository.save(prompt)
        promptSyncService.exportToNewVersion("Auto-export after creating prompt: ${name}")
        return saved
    }

    @Transactional
    fun update(name: String, content: String): PromptEntity {
        val prompt = getPromptEntity(name)
        prompt.content = content

        val saved = promptRepository.save(prompt)
        promptSyncService.exportToNewVersion("Auto-export after updating prompt: $name")
        return saved
    }

    @Transactional
    fun delete(name: String) {
        if (name.startsWith("DEFAULT_")) {
            throw IllegalArgumentException("Default prompt cannot be deleted: $name")
        }
        promptRepository.deleteByName(name)
        promptSyncService.exportToNewVersion("Auto-export after deleting prompt: $name")
    }

    fun getOrCreatePrompt(
        pipelineName: String,
        stepType: String,
        type: PromptType,
        providedName: String?,
        providedContent: String?
    ): PromptEntity {
        if (!providedName.isNullOrBlank()) {
            val existing = promptRepository.findByName(providedName)
            if (existing != null) {
                if (!providedContent.isNullOrBlank() && providedContent != existing.content) {
                    existing.content = providedContent
                    return promptRepository.save(existing)
                }
                return existing
            }
        }

        if (!providedContent.isNullOrBlank()) {
            val promptName = providedName ?: "$pipelineName-$stepType-${type.name.lowercase()}"
            val existing = promptRepository.findByName(promptName)
            return if (existing != null) {
                existing.content = providedContent
                promptRepository.save(existing)
            } else {
                promptRepository.save(PromptEntity(type = type, name = promptName, content = providedContent))
            }
        }

        val defaultPromptName = "DEFAULT_${stepType}_${type.name}"
        return promptRepository.findByName(defaultPromptName)!!
    }
}