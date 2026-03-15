package com.kdob.piq.ai.application.service.answers

import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

class ShortAnswersGenerationStepServiceTest {

    private val generator = mock(OpenAiChatService::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val service = ShortAnswersGenerationStepService(generator, repository, artifactStorage)

    private fun createPipelineWithAnswersArtifact(
        name: String,
        topicKey: String,
        answersStatus: ArtifactStatus = ArtifactStatus.APPROVED
    ): PipelineEntity {
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)

        val answersStep = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "LONG_ANSWERS_GENERATION",
            stepOrder = 0
        )
        val answersArtifact = AnswersArtifactEntity(pipeline = pipeline)
        answersArtifact.status = answersStatus

        val topicQA = TopicQAEntity(key = "java-basics", name = "Java Basics", answersArtifact = answersArtifact)
        topicQA.entries.add(QAEntryEntity(
            questionText = "What is Java?",
            level = "junior",
            answer = "Java is a statically-typed, class-based language...",
            topicQA = topicQA
        ))
        topicQA.entries.add(QAEntryEntity(
            questionText = "Explain GC",
            level = "senior",
            answer = "Garbage collection in Java is an automatic memory management process...",
            topicQA = topicQA
        ))
        answersArtifact.topicsWithQA.add(topicQA)

        answersStep.artifact = answersArtifact
        pipeline.steps.add(answersStep)

        val shortAnswersStep = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "SHORT_ANSWERS_GENERATION",
            stepOrder = 1,
            systemPrompt = PromptEntity(PromptType.SYSTEM, "$name-sys", "System {{topicName}} {{level}} {{questionText}} {{answer}}"),
            userPrompt = PromptEntity(PromptType.USER, "$name-usr", "User {{topicName}} {{level}} {{questionText}} {{answer}}")
        )
        pipeline.steps.add(shortAnswersStep)

        return pipeline
    }

    @Test
    fun `should return correct step type`() {
        assertEquals("SHORT_ANSWERS_GENERATION", service.getStepType())
    }

    @Test
    fun `should throw exception if answers step not found`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "java")
        val step = PipelineStepEntity(pipeline = pipeline, stepType = "SHORT_ANSWERS_GENERATION", stepOrder = 0)
        pipeline.steps.add(step)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if answers artifact is not approved`() {
        val pipeline = createPipelineWithAnswersArtifact("test", "java", ArtifactStatus.PENDING_FOR_APPROVAL)
        val step = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }!!

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should generate short answers for all questions`() {
        val pipeline = createPipelineWithAnswersArtifact("java-pipeline", "java")

        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            shortAnswer: |
              Java is a statically-typed OOP language running on the JVM.
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }!!
        service.generate(step)

        verify(repository).save(pipeline)
        verify(artifactStorage).saveShortAnswersArtifact(eq("java") ?: "", eq("java-pipeline") ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.WAITING_ARTIFACT_APPROVAL, pipeline.status)

        val artifact = step.artifact as? AnswersArtifactEntity
        assertNotNull(artifact)
        assertEquals(1, artifact!!.topicsWithQA.size)

        val entries = artifact.topicsWithQA.first().entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.answer != null })
        assertTrue(entries.all { it.shortAnswer != null })
    }

    @Test
    fun `should preserve original answers in short answers artifact`() {
        val pipeline = createPipelineWithAnswersArtifact("java-pipeline", "java")

        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            shortAnswer: |
              Short version of the answer.
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }!!
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val entries = artifact!!.topicsWithQA.first().entries
        assertTrue(entries.any { it.answer == "Java is a statically-typed, class-based language..." })
        assertTrue(entries.any { it.answer == "Garbage collection in Java is an automatic memory management process..." })
    }
}
