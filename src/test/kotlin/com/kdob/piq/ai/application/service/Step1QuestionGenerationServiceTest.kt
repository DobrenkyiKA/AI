package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.service.step1.GeminiQuestionGenerator
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class Step1QuestionGenerationServiceTest {

    private val generator = mock(GeminiQuestionGenerator::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val service = Step1QuestionGenerationService(generator, repository, artifactStorage)

    @Test
    fun `should throw exception if pipeline not found`() {
        `when`(repository.findByName("unknown")).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            service.generate("unknown")
        }
    }

    @Test
    fun `should throw exception if artifact step 0 not found`() {
        val pipeline = PipelineEntity(name = "test")
        `when`(repository.findByName("test")).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate("test")
        }
    }

    @Test
    fun `should throw exception if artifact step 0 is not APPROVED`() {
        val pipeline = PipelineEntity(name = "test")
        val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.PENDING_FOR_APPROVAL
        pipeline.artifactStep0 = artifactStep0
        `when`(repository.findByName("test")).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate("test")
        }
    }

    @Test
    fun `should generate questions and save them`() {
        val pipelineName = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = pipelineName)
        val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.APPROVED
        
        val topic = Step0TopicEntity(
            key = "java-gc",
            title = "JVM Garbage Collection",
            description = "Memory management",
            artifactStep0 = artifactStep0,
            constraints = ConstraintsEntity(
                targetAudience = "backend-engineers",
                experienceLevel = "mid-to-senior",
                intendedUsage = listOf("interview"),
                exclusions = emptyList(),
                questionCount = 2
            )
        )
        artifactStep0.topics.add(topic)
        pipeline.artifactStep0 = artifactStep0

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(generator.generateQuestions(anyString() ?: "")).thenReturn("""
            questions:
              - Question 1
              - Question 2
        """.trimIndent())

        service.generate(pipelineName)

        verify(repository).save(pipeline)
        verify(artifactStorage).saveStep1Questions(eq(pipelineName) ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.STEP_1_PENDING_FOR_APPROVAL, pipeline.status)
        
        assertEquals(1, pipeline.artifactStep1?.topicsWithQuestions?.size)
        val topicWithQuestions = pipeline.artifactStep1?.topicsWithQuestions?.first()
        assertEquals("java-gc", topicWithQuestions?.key)
        assertEquals(2, topicWithQuestions?.questions?.size)
    }
}
