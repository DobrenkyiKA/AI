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

    private fun addTopicsStep(pipeline: PipelineEntity, artifact: TopicsPipelineArtifactEntity? = null) {
        val step = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "TOPICS_GENERATION",
            stepOrder = 0
        )
        step.artifact = artifact
        pipeline.steps.add(step)
    }

    @Test
    fun `should throw exception if topics artifact not found`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "test-topic")
        addStepToPipeline(pipeline)
        val step = pipeline.steps.find { it.stepType == service.getStepType() }!!

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if topics artifact is not APPROVED`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "test-topic")
        addStepToPipeline(pipeline)
        val topicsArtifact = TopicsPipelineArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        addTopicsStep(pipeline, topicsArtifact)
        val step = pipeline.steps.find { it.stepType == service.getStepType() }!!

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should generate questions and save them`() {
        val pipelineName = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = "java-core")
        addStepToPipeline(pipeline)
        val topicsArtifact = TopicsPipelineArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.APPROVED
        
        val topic = PipelineTopicEntity(
            key = "java-gc",
            name = "JVM Garbage Collection",
            parentTopicKey = null,
            coverageArea = "Memory management",
            topicsArtifact = topicsArtifact,
        )
        topicsArtifact.topics.add(topic)
        addTopicsStep(pipeline, topicsArtifact)

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(repository.saveAndFlush(pipeline)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - Question 1
              - Question 2
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == service.getStepType() }!!
        service.generate(step)

        verify(repository).save(pipeline)
        verify(artifactStorage).saveQuestionsArtifact(eq("java-core") ?: "", eq(pipelineName) ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.QUESTIONS_PENDING_FOR_APPROVAL, pipeline.status)
        
        val questionsStep = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }
        val questionsArtifact = questionsStep?.artifact as? QuestionsPipelineArtifactEntity
        assertEquals(1, questionsArtifact?.topicsWithQuestions?.size)
        val topicWithQuestions = questionsArtifact?.topicsWithQuestions?.first()
        assertEquals("java-gc", topicWithQuestions?.key)
        assertEquals(2, topicWithQuestions?.questions?.size)
    }

    @Test
    fun `should handle questions with special characters if quoted`() {
        val pipelineName = "java-pipeline"
        val pipeline = PipelineEntity(name = pipelineName, topicKey = "java-core")
        addStepToPipeline(pipeline)
        val artifactStep0 = TopicsPipelineArtifactEntity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.APPROVED
        
        val topic = PipelineTopicEntity(
            key = "java-annotations",
            name = "Annotations",
            parentTopicKey = null,
            coverageArea = "Annotations basics",
            topicsArtifact = artifactStep0,
        )
        artifactStep0.topics.add(topic)
        addTopicsStep(pipeline, artifactStep0)

        `when`(repository.findByName(pipelineName)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - "@Override annotation purpose?"
              - "What is @FunctionalInterface?"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == service.getStepType() }!!
        service.generate(step)

        val questionsStep = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }
        val questionsArtifact = questionsStep?.artifact as? QuestionsPipelineArtifactEntity
        val topicWithQuestions = questionsArtifact?.topicsWithQuestions?.first()
        assertEquals(2, topicWithQuestions?.questions?.size)
        assertTrue(topicWithQuestions?.questions?.contains("@Override annotation purpose?") == true)
    }
}
