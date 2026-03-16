package com.kdob.piq.ai.application.service.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.client.storage.StorageServiceClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.domain.repository.PromptRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StartupPromptLoader(
    private val promptSyncService: PromptSyncService,
    private val storageClient: StorageServiceClient,
    private val promptRepository: PromptRepository
) {
    private val logger = LoggerFactory.getLogger(StartupPromptLoader::class.java)
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun loadPromptsFromStorage() {
        try {
            logger.info("Loading prompts from storage on startup")
            storageClient.refresh()
            val versions = storageClient.getVersions()
            if (versions.isNotEmpty()) {
                val lastVersion = versions.last()
                logger.info("Importing prompts from version: $lastVersion")
                promptSyncService.importFromVersion(lastVersion)
            } else {
                logger.warn("No prompt versions found in storage. Seeding from resources.")
                seedFromResources()
            }
        } catch (e: Exception) {
            logger.error("Failed to load prompts from storage on startup", e)
        }
    }

    private fun seedFromResources() {
        try {
            val resource = ClassPathResource("prompts/default-prompts.yaml")
            if (!resource.exists()) {
                logger.warn("Default prompts resource file not found: prompts/default-prompts.yaml")
                return
            }

            val defaultPrompts = yamlMapper.readValue(resource.inputStream, DefaultPromptsDto::class.java)
            
            defaultPrompts.prompts.forEach { promptDto ->
                val existingPrompt = promptRepository.findByName(promptDto.name)
                if (existingPrompt == null) {
                    logger.info("Creating default prompt from resource: ${promptDto.name}")
                    promptRepository.save(
                        PromptEntity(
                            type = promptDto.type,
                            name = promptDto.name,
                            content = promptDto.content.trim()
                        )
                    )
                }
            }
            
            logger.info("Exporting seeded prompts to storage as 'Auto v1'")
            promptSyncService.exportToVersion("Auto v1", "Initial seed from resources")
        } catch (e: Exception) {
            logger.error("Failed to seed prompts from resources", e)
        }
    }
}
