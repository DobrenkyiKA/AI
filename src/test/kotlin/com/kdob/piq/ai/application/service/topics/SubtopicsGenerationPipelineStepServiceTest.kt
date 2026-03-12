package com.kdob.piq.ai.application.service.topics

import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class SubtopicsGenerationPipelineStepServiceTest {

    private val generator = mock(GeminiChat::class.java)
    private val pipelineRepository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)

    private val service = SubtopicsGenerationPipelineStepService(
        generator,
        pipelineRepository,
        artifactStorage,
        questionCatalogClient
    )

    @Test
    fun `should generate subtopics with parent context`() {
        val pipeline = PipelineEntity(name = "test-pipeline", topicKey = "topic3")
        val step = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "SUBTOPICS_GENERATION",
            stepOrder = 0,
            systemPrompt = PromptEntity(type = PromptType.SYSTEM, name = "sys", content = "System prompt {{parentTopics}}"),
            userPrompt = PromptEntity(type = PromptType.USER, name = "usr", content = "User prompt {{topicName}}")
        )

        val topic3 = TopicClientResponse(key = "topic3", name = "Topic 3", path = "/topic1/topic2/topic3", coverageArea = "Area 3")
        val topic1 = TopicClientResponse(key = "topic1", name = "Topic 1", path = "/topic1", coverageArea = "Area 1")
        val topic2 = TopicClientResponse(key = "topic2", name = "Topic 2", path = "/topic1/topic2", coverageArea = "Area 2")

        `when`(questionCatalogClient.findTopic("topic3")).thenReturn(topic3)
        `when`(questionCatalogClient.findTopic("topic1")).thenReturn(topic1)
        `when`(questionCatalogClient.findTopic("topic2")).thenReturn(topic2)

        val llmOutput = """
            topics:
              - key: "sub1"
                name: "Subtopic 1"
                coverageArea: "Subarea 1"
        """.trimIndent()

        `when`(generator.executePrompt(anyString(), anyString())).thenReturn(llmOutput)

        service.generate(pipeline, step)

        val systemPromptCaptor = ArgumentCaptor.forClass(String::class.java)
        val userPromptCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(generator).executePrompt(systemPromptCaptor.capture() ?: "", userPromptCaptor.capture() ?: "")

        val systemPrompt = systemPromptCaptor.value
        assertTrue(systemPrompt.contains("Parent Topic: Topic 1"))
        assertTrue(systemPrompt.contains("Coverage Area: Area 1"))
        assertTrue(systemPrompt.contains("Parent Topic: Topic 2"))
        assertTrue(systemPrompt.contains("Coverage Area: Area 2"))

        val userPrompt = userPromptCaptor.value
        assertTrue(userPrompt.contains("User prompt Topic 3"))

        verify(artifactStorage).saveTopicsArtifact(eq("topic3") ?: "", eq("test-pipeline") ?: "", anyString() ?: "")
        verify(pipelineRepository).save(pipeline ?: PipelineEntity("", ""))
    }
}
