package com.kdob.piq.ai.application.service.answers

import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class ShortAnswersGenerationPipelineStepServiceTest {

    private val generator = mock(OpenAiChatService::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val generationLogRepository = mock(GenerationLogRepository::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java)
    private val service = ShortAnswersGenerationPipelineStepService(generator, repository, artifactStorage, generationLogRepository, transactionManager)

    @BeforeEach
    fun setup() {
        val transactionStatus = mock(TransactionStatus::class.java)
        `when`(transactionManager.getTransaction(any())).thenReturn(transactionStatus)
    }

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

        val topicQA = TopicQAEntity(key = "java-basics", name = "Java Basics", answersArtifact = answersArtifact).apply {
            parentChain = "Java"
        }
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
            systemPrompt = PromptEntity(PromptType.SYSTEM, "$name-sys", "System {{topicName}} {{parentChain}} {{level}} {{questionText}} {{answer}}"),
            userPrompt = PromptEntity(PromptType.USER, "$name-usr", "User {{topicName}} {{parentChain}} {{level}} {{questionText}} {{answer}}")
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
        pipeline.id = 1L
        val step = PipelineStepEntity(pipeline = pipeline, stepType = "SHORT_ANSWERS_GENERATION", stepOrder = 0)
        step.id = 2L
        pipeline.steps.add(step)

        `when`(repository.findById(1L)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if answers artifact is not approved`() {
        val pipeline = createPipelineWithAnswersArtifact("test", "java", ArtifactStatus.PENDING_FOR_APPROVAL)
        pipeline.id = 1L
        val step = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }!!
        step.id = 2L

        `when`(repository.findById(1L)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should generate short answers for all questions`() {
        val pipeline = createPipelineWithAnswersArtifact("java-pipeline", "java")
        pipeline.id = 1L

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            shortAnswer: |
              Java is a statically-typed OOP language running on the JVM.
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        verify(generator, atLeastOnce()).executePrompt(
            contains("System Java Basics Java junior What is Java? Java is a statically-typed, class-based language..."),
            contains("User Java Basics Java junior What is Java? Java is a statically-typed, class-based language...")
        )

        verify(repository, atLeastOnce()).saveAndFlush(pipeline)
        verify(artifactStorage, atLeastOnce()).saveShortAnswersArtifact(eq("java") ?: "", eq("java-pipeline") ?: "", anyString() ?: "")
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
        pipeline.id = 1L

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            shortAnswer: |
              Short version of the answer.
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val entries = artifact!!.topicsWithQA.first().entries
        assertTrue(entries.any { it.answer == "Java is a statically-typed, class-based language..." })
        assertTrue(entries.any { it.answer == "Garbage collection in Java is an automatic memory management process..." })
    }
}
