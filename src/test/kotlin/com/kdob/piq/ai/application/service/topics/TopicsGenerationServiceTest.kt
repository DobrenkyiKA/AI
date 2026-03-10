package com.kdob.piq.ai.application.service.topics

import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class TopicsGenerationServiceTest {

    private val generator = mock(GeminiChat::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val service = TopicsGenerationService(generator, repository, artifactStorage, questionCatalogClient)

    @Test
    fun `should generate topics and save them`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)

        val topicResponse = TopicClientResponse(
            key = topicKey,
            name = "Java Core",
            path = "/java-core",
            coverageArea = "Core Java features",
            exclusions = "Spring"
        )

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        `when`(generator.executePrompt(anyString() ?: "")).thenReturn("""
            topics:
              - key: java-fundamentals
                name: Java Fundamentals
                parentTopicKey: null
                coverageArea: Basics of Java
              - key: java-collections
                name: Java Collections
                parentTopicKey: java-fundamentals
                coverageArea: List, Set, Map
        """.trimIndent())

        service.generate(pipelineName)

        verify(repository).save(pipeline)
        verify(artifactStorage).saveTopicsArtifact(eq(pipelineName) ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.DRAFT, pipeline.status)
        assertEquals(ArtifactStatus.PENDING_FOR_APPROVAL, pipeline.topicsArtifact?.status)
        
        assertEquals(2, pipeline.topicsArtifact?.topics?.size)
        val fundamentals = pipeline.topicsArtifact?.topics?.find { it.key == "java-fundamentals" }
        assertEquals("Java Fundamentals", fundamentals?.name)
        // Top level subtopic should have parentTopicKey set to the pipeline's topicKey
        assertEquals(topicKey, fundamentals?.parentTopicKey)

        val collections = pipeline.topicsArtifact?.topics?.find { it.key == "java-collections" }
        assertEquals("Java Collections", collections?.name)
        assertEquals("java-fundamentals", collections?.parentTopicKey)
    }

    @Test
    fun `should not include exclusions in prompt if they are blank`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)

        val topicResponse = TopicClientResponse(
            key = topicKey,
            name = "Java Core",
            path = "/java-core",
            coverageArea = "Core Java features",
            exclusions = ""
        )

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        `when`(generator.executePrompt(anyString() ?: "")).thenReturn("""
            topics:
              - key: java-fundamentals
                name: Java Fundamentals
                parentTopicKey: null
                coverageArea: Basics of Java
        """.trimIndent())

        service.generate(pipelineName)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(generator).executePrompt(captor.capture() ?: "")
        
        val prompt = captor.value
        assertFalse(prompt.contains("Exclusions (DO NOT INCLUDE):"), "Prompt should not contain 'Exclusions (DO NOT INCLUDE):'")
        assertFalse(prompt.contains("- Strict Exclusions:"), "Prompt should not contain rule '- Strict Exclusions:'")
    }

    @Test
    fun `should include exclusions in prompt if they are not blank`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)

        val topicResponse = TopicClientResponse(
            key = topicKey,
            name = "Java Core",
            path = "/java-core",
            coverageArea = "Core Java features",
            exclusions = "Spring"
        )

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        `when`(generator.executePrompt(anyString() ?: "")).thenReturn("""
            topics:
              - key: java-fundamentals
                name: Java Fundamentals
                parentTopicKey: null
                coverageArea: Basics of Java
        """.trimIndent())

        service.generate(pipelineName)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(generator).executePrompt(captor.capture() ?: "")
        
        val prompt = captor.value
        assertTrue(prompt.contains("Exclusions (DO NOT INCLUDE): Spring"), "Prompt should contain 'Exclusions (DO NOT INCLUDE): Spring'")
        assertTrue(prompt.contains("- Strict Exclusions: Do not include ANY topics or subtopics that fall under the exclusions list."), "Prompt should contain rule '- Strict Exclusions: ...'")
    }

    @Test
    fun `should succeed if LLM returns valid YAML with @ sign quoted`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        val topicResponse = TopicClientResponse(topicKey, "Java Core", "/java-core", "Core", "")

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        
        // Simulating the corrected output with quotes
        `when`(generator.executePrompt(anyString() ?: "")).thenReturn("""
            topics:
              - key: "java-annotations"
                name: "Annotations"
                parentTopicKey: "java-core"
                coverageArea: "@FunctionalInterface annotation"
        """.trimIndent())

        service.generate(pipelineName)
        
        assertEquals(1, pipeline.topicsArtifact?.topics?.size)
        assertEquals("@FunctionalInterface annotation", pipeline.topicsArtifact?.topics?.first()?.coverageArea)
    }
}
