package com.kdob.piq.ai.application.service.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.client.storage.StorageServiceClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PromptSyncService(
    private val promptRepository: PromptRepository,
    private val storageServiceClient: StorageServiceClient
) {
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Transactional
    fun exportToVersion(version: String, commitMessage: String) {
        val prompts = promptRepository.findAll()
        val dto = DefaultPromptsDto(
            prompts = prompts.map { 
                DefaultPromptDto(
                    name = it.name,
                    type = it.type,
                    content = it.content
                )
            }
        )
        val content = yamlMapper.writeValueAsString(dto)
        storageServiceClient.savePromptFile(version, "prompts.yaml", content)
        storageServiceClient.commit(version, commitMessage)
    }

    @Transactional
    fun importFromVersion(version: String) {
        val content = storageServiceClient.getPromptFile(version, "prompts.yaml")
        if (content != null) {
            val dto = yamlMapper.readValue(content, DefaultPromptsDto::class.java)
            val importedNames = dto.prompts.map { it.name }.toSet()

            dto.prompts.forEach { promptDto ->
                val existing = promptRepository.findByName(promptDto.name)
                if (existing != null) {
                    existing.content = promptDto.content
                    existing.type = promptDto.type
                    promptRepository.save(existing)
                } else {
                    promptRepository.save(
                        PromptEntity(
                            name = promptDto.name,
                            type = promptDto.type,
                            content = promptDto.content
                        )
                    )
                }
            }

            // Optional: delete prompts not in imported list?
            // For prompts, maybe it's better to keep them unless explicitly deleted.
            // But if we want it "the same way like question versions", let's see what CatalogSyncService does.
            // CatalogSyncService deletes missing questions.
            
            val allPrompts = promptRepository.findAll()
            allPrompts.filter { it.name !in importedNames }.forEach {
                promptRepository.deleteByName(it.name)
            }
        }
    }
}
