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

class LongAnswersGenerationPipelineStepServiceTest {

    private val generator = mock(OpenAiChatService::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val generationLogRepository = mock(GenerationLogRepository::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java)
    private val service = LongAnswersGenerationPipelineStepService(generator, repository, artifactStorage, generationLogRepository, transactionManager)

    @BeforeEach
    fun setup() {
        val transactionStatus = mock(TransactionStatus::class.java)
        `when`(transactionManager.getTransaction(any())).thenReturn(transactionStatus)
    }

    private fun createPipelineWithQuestionsArtifact(
        name: String,
        topicKey: String,
        questionsStatus: ArtifactStatus = ArtifactStatus.APPROVED
    ): PipelineEntity {
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)

        val questionsStep = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "QUESTIONS_GENERATION",
            stepOrder = 0
        )
        val questionsArtifact = AnswersArtifactEntity(pipeline = pipeline)
        questionsArtifact.status = questionsStatus

        val topicQA = TopicQAEntity(key = "java-basics", name = "Java Basics", answersArtifact = questionsArtifact).apply {
            parentChain = "Java"
        }
        topicQA.entries.add(QAEntryEntity(questionText = "What is Java?", level = "junior", topicQA = topicQA))
        topicQA.entries.add(QAEntryEntity(questionText = "Explain GC", level = "senior", topicQA = topicQA))
        questionsArtifact.topicsWithQA.add(topicQA)

        questionsStep.artifact = questionsArtifact
        pipeline.steps.add(questionsStep)

        val answersStep = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "LONG_ANSWERS_GENERATION",
            stepOrder = 1,
            systemPrompt = PromptEntity(PromptType.SYSTEM, "$name-sys", "System {{topicName}} {{parentChain}} {{level}} {{questionText}}"),
            userPrompt = PromptEntity(PromptType.USER, "$name-usr", "User {{topicName}} {{parentChain}} {{level}} {{questionText}}")
        )
        pipeline.steps.add(answersStep)

        return pipeline
    }

    @Test
    fun `should return correct step type`() {
        assertEquals("LONG_ANSWERS_GENERATION", service.getStepType())
    }

    @Test
    fun `should throw exception if questions step not found`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "java")
        pipeline.id = 1L
        val step = PipelineStepEntity(pipeline = pipeline, stepType = "LONG_ANSWERS_GENERATION", stepOrder = 0)
        step.id = 2L
        pipeline.steps.add(step)

        `when`(repository.findById(1L)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if questions artifact is not approved`() {
        val pipeline = createPipelineWithQuestionsArtifact("test", "java", ArtifactStatus.PENDING_FOR_APPROVAL)
        pipeline.id = 1L
        val step = pipeline.steps.find { it.stepType == "LONG_ANSWERS_GENERATION" }!!
        step.id = 2L

        `when`(repository.findById(1L)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should generate answers for all questions`() {
        val pipeline = createPipelineWithQuestionsArtifact("java-pipeline", "java")
        pipeline.id = 1L

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            answer: |
              This is a comprehensive answer about Java.
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "LONG_ANSWERS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        verify(generator, atLeastOnce()).executePrompt(
            contains("System Java Basics Java junior What is Java?"),
            contains("User Java Basics Java junior What is Java?")
        )

        verify(repository, atLeastOnce()).saveAndFlush(pipeline)
        verify(artifactStorage, atLeastOnce()).saveAnswersArtifact(eq("java") ?: "", eq("java-pipeline") ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.WAITING_ARTIFACT_APPROVAL, pipeline.status)

        val artifact = step.artifact as? AnswersArtifactEntity
        assertNotNull(artifact)
        assertEquals(1, artifact!!.topicsWithQA.size)

        val entries = artifact.topicsWithQA.first().entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.answer != null })
        assertTrue(entries.all { it.shortAnswer == null })
    }

    @Test
    fun `should handle raw text answer when YAML parsing fails`() {
        val pipeline = createPipelineWithQuestionsArtifact("java-pipeline", "java")
        pipeline.id = 1L

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn(
            "This is a plain text answer without YAML format."
        )

        val step = pipeline.steps.find { it.stepType == "LONG_ANSWERS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val entries = artifact!!.topicsWithQA.first().entries
        assertTrue(entries.all { it.answer == "This is a plain text answer without YAML format." })
    }
}
