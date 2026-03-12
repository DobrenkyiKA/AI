package com.kdob.piq.ai.application.service.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.repository.PromptRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.core.io.ClassPathResource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull

class DefaultPromptLoaderTest {

    @Test
    fun `should parse default-prompts yaml`() {
        val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val resource = ClassPathResource("prompts/default-prompts.yaml")
        assertTrue(resource.exists())
        
        val defaultPrompts = yamlMapper.readValue(resource.inputStream, DefaultPromptsDto::class.java)
        assertNotNull(defaultPrompts)
        
        val promptNames = defaultPrompts.prompts.map { it.name }
        assertTrue(promptNames.contains("DEFAULT_TOPICS_GENERATION_SYSTEM"))
        assertTrue(promptNames.contains("DEFAULT_TOPICS_GENERATION_USER"))
        assertTrue(promptNames.contains("DEFAULT_QUESTIONS_GENERATION_SYSTEM"))
        assertTrue(promptNames.contains("DEFAULT_QUESTIONS_GENERATION_USER"))
        assertTrue(promptNames.contains("DEFAULT_SUBTOPICS_GENERATION_SYSTEM"))
        assertTrue(promptNames.contains("DEFAULT_SUBTOPICS_GENERATION_USER"))
    }
}
