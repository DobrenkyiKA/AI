package com.kdob.piq.ai.application.service.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DefaultPromptLoader(
    private val promptRepository: PromptRepository
) {
    private val logger = LoggerFactory.getLogger(DefaultPromptLoader::class.java)
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun loadDefaultPrompts() {
        try {
            val resource = ClassPathResource("prompts/default-prompts.yaml")
            if (!resource.exists()) {
                logger.warn("Default prompts file not found: prompts/default-prompts.yaml")
                return
            }

            val defaultPrompts = yamlMapper.readValue(resource.inputStream, DefaultPromptsDto::class.java)
            
            defaultPrompts.prompts.forEach { promptDto ->
                val existingPrompt = promptRepository.findByName(promptDto.name)
                if (existingPrompt == null) {
                    logger.info("Creating default prompt: ${promptDto.name}")
                    promptRepository.save(
                        PromptEntity(
                            type = promptDto.type,
                            name = promptDto.name,
                            content = promptDto.content.trim()
                        )
                    )
                } else {
                    logger.debug("Default prompt already exists: ${promptDto.name}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load default prompts", e)
        }
    }
}

data class DefaultPromptsDto(
    val prompts: List<DefaultPromptDto> = emptyList()
)

data class DefaultPromptDto(
    val name: String,
    val type: PromptType,
    val content: String
)
