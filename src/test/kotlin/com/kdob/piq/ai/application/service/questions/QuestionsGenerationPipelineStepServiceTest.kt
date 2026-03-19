package com.kdob.piq.ai.application.service.questions

import com.kdob.piq.ai.application.service.ai.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
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

class QuestionsGenerationPipelineStepServiceTest {
    private val generator = mock(OpenAiChatService::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val generationLogRepository = mock(GenerationLogRepository::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java)
    private val service = QuestionsGenerationPipelineStepService(generator, repository, artifactStorage, questionCatalogClient, generationLogRepository, transactionManager)

    @BeforeEach
    fun setup() {
        val transactionStatus = mock(TransactionStatus::class.java)
        `when`(transactionManager.getTransaction(any())).thenReturn(transactionStatus)

        `when`(questionCatalogClient.findTopic("java")).thenReturn(
            TopicClientResponse(key = "java", name = "Java", path = "/jvm-ecosystem/java-languages/java")
        )
        `when`(questionCatalogClient.findTopic("jvm-ecosystem")).thenReturn(
            TopicClientResponse(key = "jvm-ecosystem", name = "JVM Ecosystem", path = "/jvm-ecosystem")
        )
        `when`(questionCatalogClient.findTopic("java-languages")).thenReturn(
            TopicClientResponse(key = "java-languages", name = "Java Languages", path = "/jvm-ecosystem/java-languages")
        )
    }

    private fun createPipeline(name: String, topicKey: String): PipelineEntity {
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)

        val topicTreeStep = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "TOPIC_TREE_GENERATION",
            stepOrder = 0
        )
        pipeline.steps.add(topicTreeStep)

        val questionsStep = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "QUESTIONS_GENERATION",
            stepOrder = 1,
            systemPrompt = PromptEntity(PromptType.SYSTEM, "$name-sys", "System {{topicKey}} {{topicName}} {{coverageArea}} {{topicType}}\nSubtopics (for branch topics, generate cross-cutting questions):\n{{childTopicsList}}\n{{parentChain}}"),
            userPrompt = PromptEntity(PromptType.USER, "$name-usr", "User {{topicKey}} {{topicName}} {{coverageArea}} {{topicType}}\nSubtopics (for branch topics, generate cross-cutting questions):\n{{childTopicsList}}\n{{parentChain}}")
        )
        pipeline.steps.add(questionsStep)

        return pipeline
    }

    private fun createTopicTreeArtifact(pipeline: PipelineEntity, status: ArtifactStatus = ArtifactStatus.APPROVED): TopicTreeArtifactEntity {
        val artifact = TopicTreeArtifactEntity(pipeline = pipeline, maxDepth = 3)
        artifact.status = status

        val rootNode = TopicTreeNodeEntity(
            key = "java",
            name = "Java",
            parentTopicKey = null,
            coverageArea = "The Java programming language",
            depth = 0,
            leaf = false,
            topicTreeArtifact = artifact
        )
        val leafNode = TopicTreeNodeEntity(
            key = "java-basics",
            name = "Java Basics",
            parentTopicKey = "java",
            coverageArea = "Fundamentals of Java",
            depth = 1,
            leaf = true,
            topicTreeArtifact = artifact
        )
        artifact.nodes.add(rootNode)
        artifact.nodes.add(leafNode)

        return artifact
    }

    @Test
    fun `should return correct step type`() {
        assertEquals("QUESTIONS_GENERATION", service.getStepType())
    }

    @Test
    fun `should throw exception if topic tree step not found`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "java")
        pipeline.id = 1L
        val step = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "QUESTIONS_GENERATION",
            stepOrder = 0
        )
        step.id = 2L
        pipeline.steps.add(step)

        `when`(repository.findById(1L)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if topic tree artifact is not approved`() {
        val pipeline = createPipeline("test", "java")
        pipeline.id = 1L
        val topicTreeArtifact = createTopicTreeArtifact(pipeline, ArtifactStatus.PENDING_FOR_APPROVAL)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        step.id = 2L

        `when`(repository.findById(1L)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should generate questions for all topics in the tree`() {
        val pipeline = createPipeline("java-pipeline", "java")
        pipeline.id = 1L
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - text: "What is Java?"
                level: "junior"
              - text: "Explain JVM internals"
                level: "senior"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        verify(repository, atLeastOnce()).saveAndFlush(pipeline)
        verify(artifactStorage, atLeastOnce()).saveQuestionsArtifact(eq("java") ?: "", eq("java-pipeline") ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.WAITING_ARTIFACT_APPROVAL, pipeline.status)

        val artifact = step.artifact as? AnswersArtifactEntity
        assertNotNull(artifact)
        assertEquals(2, artifact!!.topicsWithQA.size) // 2 topics: java, java-basics
        assertTrue(artifact.topicsWithQA.all { it.entries.size == 2 })

        val javaBasics = artifact.topicsWithQA.find { it.key == "java-basics" }
        assertNotNull(javaBasics)
        assertEquals("JVM Ecosystem > Java Languages > Java", javaBasics!!.parentChain)

        val java = artifact.topicsWithQA.find { it.key == "java" }
        assertNotNull(java)
        assertEquals("JVM Ecosystem > Java Languages", java!!.parentChain)
    }

    @Test
    fun `should remove subtopics block if no children`() {
        val pipeline = createPipeline("java-pipeline", "java")
        pipeline.id = 1L
        
        // Root node "java" has one child "java-basics".
        // "java-basics" has no children.
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        step.id = 2L

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("questions:\n  - text: \"Q1\"\n    level: \"junior\"")

        service.generate(step)

        // Capture prompts for "java-basics" (leaf node, no children)
        verify(generator, atLeastOnce()).executePrompt(
            argThat { it != null && it.contains("System java-basics") && !it.contains("Subtopics (for branch topics") } ?: "",
            argThat { it != null && it.contains("User java-basics") && !it.contains("Subtopics (for branch topics") } ?: ""
        )
        
        // Capture prompts for "java" (branch node, has child "java-basics")
        verify(generator, atLeastOnce()).executePrompt(
            argThat { it != null && it.contains("System java Java") && it.contains("Subtopics (for branch topics") && it.contains("- Java Basics: Fundamentals of Java") } ?: "",
            argThat { it != null && it.contains("User java Java") && it.contains("Subtopics (for branch topics") && it.contains("- Java Basics: Fundamentals of Java") } ?: ""
        )
    }

    @Test
    fun `should parse questions with levels correctly`() {
        val pipeline = createPipeline("java-pipeline", "java")
        pipeline.id = 1L
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - text: "Basic question"
                level: "junior"
              - text: "Advanced question"
                level: "principal"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val allEntries = artifact!!.topicsWithQA.flatMap { it.entries }
        assertTrue(allEntries.any { it.level == "junior" })
        assertTrue(allEntries.any { it.level == "principal" })
    }

    @Test
    fun `should handle simple string questions with default level`() {
        val pipeline = createPipeline("java-pipeline", "java")
        pipeline.id = 1L
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        `when`(repository.findById(1L)).thenReturn(pipeline)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - "What is Java?"
              - "Explain polymorphism"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        step.id = 2L
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val allEntries = artifact!!.topicsWithQA.flatMap { it.entries }
        assertTrue(allEntries.all { it.level == "mid" }) // default level
    }
}
