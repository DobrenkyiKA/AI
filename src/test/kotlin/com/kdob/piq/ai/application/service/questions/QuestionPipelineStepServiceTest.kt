package com.kdob.piq.ai.application.service.questions

import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class QuestionPipelineStepServiceTest {

    private val generator = mock(GeminiChat::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val service = QuestionPipelineStepService(generator, repository, artifactStorage)

    private fun addStepToPipeline(pipeline: PipelineEntity) {
        pipeline.steps.add(
            PipelineStepEntity(
                pipeline = pipeline,
                stepType = "QUESTIONS_GENERATION",
                stepOrder = 0,
                systemPrompt = PromptEntity(PromptType.SYSTEM, "${pipeline.name}-sys", "System"),
                userPrompt = PromptEntity(PromptType.USER, "${pipeline.name}-usr", "User")
            )
        )
    }

    @Test
    fun `should throw exception if pipeline not found`() {
        `when`(repository.findByName("unknown")).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            service.generate("unknown")
        }
    }

    @Test
    fun `should throw exception if topics artifact not found`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "test-topic")
        addStepToPipeline(pipeline)
        `when`(repository.findByName("test")).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate("test")
        }
    }

    @Test
    fun `should throw exception if topics artifact is not APPROVED`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "test-topic")
        addStepToPipeline(pipeline)
        val topicsArtifact = TopicsArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        pipeline.topicsArtifact = topicsArtifact
        `when`(repository.findByName("test")).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate("test")
        }
    }

    @Test
    fun `should generate questions and save them`() {
        val pipelineName = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = "java-core")
        addStepToPipeline(pipeline)
        val topicsArtifact = TopicsArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.APPROVED
        
        val topic = PipelineTopicEntity(
            key = "java-gc",
            name = "JVM Garbage Collection",
            parentTopicKey = null,
            coverageArea = "Memory management",
            topicsArtifact = topicsArtifact,
        )
        topicsArtifact.topics.add(topic)
        pipeline.topicsArtifact = topicsArtifact

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(repository.saveAndFlush(pipeline)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - Question 1
              - Question 2
        """.trimIndent())

        service.generate(pipelineName)

        verify(repository).save(pipeline)
        verify(artifactStorage).saveQuestionsArtifact(eq(pipelineName) ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.QUESTIONS_PENDING_FOR_APPROVAL, pipeline.status)
        
        assertEquals(1, pipeline.questionsArtifact?.topicsWithQuestions?.size)
        val topicWithQuestions = pipeline.questionsArtifact?.topicsWithQuestions?.first()
        assertEquals("java-gc", topicWithQuestions?.key)
        assertEquals(2, topicWithQuestions?.questions?.size)
    }

    @Test
    fun `should handle questions with special characters if quoted`() {
        val pipelineName = "java-pipeline"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = "java-core")
        addStepToPipeline(pipeline)
        val artifactStep0 = TopicsArtifactEntity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.APPROVED
        
        val topic = PipelineTopicEntity(
            key = "java-annotations",
            name = "Annotations",
            parentTopicKey = null,
            coverageArea = "Annotations basics",
            topicsArtifact = artifactStep0,
        )
        artifactStep0.topics.add(topic)
        pipeline.topicsArtifact = artifactStep0

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - "@Override annotation purpose?"
              - "What is @FunctionalInterface?"
        """.trimIndent())

        service.generate(pipelineName)

        val topicWithQuestions = pipeline.questionsArtifact?.topicsWithQuestions?.first()
        assertEquals(2, topicWithQuestions?.questions?.size)
        assertTrue(topicWithQuestions?.questions?.contains("@Override annotation purpose?") == true)
    }
}
