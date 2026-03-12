package com.kdob.piq.ai.application.service.topics

import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsPipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class TopicPipelineStepServiceTest {

    private val generator = mock(GeminiChat::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val service = TopicPipelineStepService(generator, repository, artifactStorage, questionCatalogClient)

    private fun addStepToPipeline(pipeline: PipelineEntity) {
        val userPromptContent = """
            Generate topics for {{topicName}}
            Exclusions (DO NOT INCLUDE): {{exclusions}}
            - Strict Exclusions: Do not include ANY topics or subtopics that fall under the exclusions list.
        """.trimIndent()
        pipeline.steps.add(
            PipelineStepEntity(
                pipeline = pipeline,
                stepType = "TOPICS_GENERATION",
                stepOrder = 0,
                systemPrompt = PromptEntity(PromptType.SYSTEM, "${pipeline.name}-sys", "System"),
                userPrompt = PromptEntity(PromptType.USER, "${pipeline.name}-usr", userPromptContent)
            )
        )
    }

    @Test
    fun `should generate topics and save them`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        addStepToPipeline(pipeline)

        val topicResponse = TopicClientResponse(
            key = topicKey,
            name = "Java Core",
            path = "/java-core",
            coverageArea = "Core Java features",
            exclusions = "Spring"
        )

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
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
        verify(artifactStorage).saveTopicsArtifact(eq(topicKey) ?: "", eq(pipelineName) ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.DRAFT, pipeline.status)
        val topicsStep = pipeline.steps.find { it.stepType == "TOPICS_GENERATION" }
        assertEquals(ArtifactStatus.PENDING_FOR_APPROVAL, topicsStep?.artifact?.status)
        
        val topicsArtifact = topicsStep?.artifact as? TopicsPipelineArtifactEntity
        assertEquals(2, topicsArtifact?.topics?.size)
        val fundamentals = topicsArtifact?.topics?.find { it.key == "java-fundamentals" }
        assertEquals("Java Fundamentals", fundamentals?.name)
        // Top level subtopic should have parentTopicKey set to the pipeline's topicKey
        assertEquals(topicKey, fundamentals?.parentTopicKey)

        val collections = topicsArtifact?.topics?.find { it.key == "java-collections" }
        assertEquals("Java Collections", collections?.name)
        assertEquals("java-fundamentals", collections?.parentTopicKey)
    }

    @Test
    fun `should not include exclusions in prompt if they are blank`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        addStepToPipeline(pipeline)

        val topicResponse = TopicClientResponse(
            key = topicKey,
            name = "Java Core",
            path = "/java-core",
            coverageArea = "Core Java features",
            exclusions = ""
        )

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            topics:
              - key: java-fundamentals
                name: Java Fundamentals
                parentTopicKey: null
                coverageArea: Basics of Java
        """.trimIndent())

        service.generate(pipelineName)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(generator).executePrompt(anyString() ?: "", captor.capture() ?: "")
        
        val prompt = captor.value
        assertFalse(prompt.contains("Exclusions (DO NOT INCLUDE):"), "Prompt should not contain 'Exclusions (DO NOT INCLUDE):'")
        assertFalse(prompt.contains("- Strict Exclusions:"), "Prompt should not contain rule '- Strict Exclusions:'")
    }

    @Test
    fun `should include exclusions in prompt if they are not blank`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        addStepToPipeline(pipeline)

        val topicResponse = TopicClientResponse(
            key = topicKey,
            name = "Java Core",
            path = "/java-core",
            coverageArea = "Core Java features",
            exclusions = "Spring"
        )

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            topics:
              - key: java-fundamentals
                name: Java Fundamentals
                parentTopicKey: null
                coverageArea: Basics of Java
        """.trimIndent())

        service.generate(pipelineName)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(generator).executePrompt(anyString() ?: "", captor.capture() ?: "")
        
        val prompt = captor.value
        assertTrue(prompt.contains("Exclusions (DO NOT INCLUDE): Spring"), "Prompt should contain 'Exclusions (DO NOT INCLUDE): Spring'")
        assertTrue(prompt.contains("- Strict Exclusions: Do not include ANY topics or subtopics that fall under the exclusions list."), "Prompt should contain rule '- Strict Exclusions: ...'")
    }

    @Test
    fun `should succeed if LLM returns valid YAML with @ sign quoted`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        addStepToPipeline(pipeline)
        val topicResponse = TopicClientResponse(topicKey, "Java Core", "/java-core", "Core", "")

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(topicResponse)
        
        // Simulating the corrected output with quotes
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            topics:
              - key: "java-annotations"
                name: "Annotations"
                parentTopicKey: "java-core"
                coverageArea: "@FunctionalInterface annotation"
        """.trimIndent())

        service.generate(pipelineName)
        
        val topicsStep = pipeline.steps.find { it.stepType == "TOPICS_GENERATION" }
        val topicsArtifact = topicsStep?.artifact as? TopicsPipelineArtifactEntity
        assertEquals(1, topicsArtifact?.topics?.size)
        assertEquals("@FunctionalInterface annotation", topicsArtifact?.topics?.first()?.coverageArea)
    }
}
