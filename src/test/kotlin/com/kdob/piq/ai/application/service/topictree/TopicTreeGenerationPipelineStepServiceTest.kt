package com.kdob.piq.ai.application.service.topictree

import com.kdob.piq.ai.application.service.ai.OpenAiChatService
import com.kdob.piq.ai.application.service.step.TopicTreeGenerationPipelineStepService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.springframework.transaction.PlatformTransactionManager

class TopicTreeGenerationPipelineStepServiceTest {

    private val generator = mock(OpenAiChatService::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val generationLogRepository = mock(GenerationLogRepository::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java)
    
    private val service = TopicTreeGenerationPipelineStepService(
        generator,
        repository,
        artifactStorage,
        questionCatalogClient,
        generationLogRepository,
        transactionManager
    )

    @BeforeEach
    fun setUp() {
        `when`(transactionManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus::class.java))
        `when`(repository.findById(anyLong())).thenAnswer { invocation ->
            val id = invocation.arguments[0] as Long
            // This is not quite right as it's hard to return the specific pipeline, 
            // but for simple tests where only one pipeline exists it might be fine.
            // Better to setup specific mocks in each test.
            null
        }
    }

    private fun createPipelineWithStep(pipelineName: String, topicKey: String): PipelineEntity {
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        pipeline.id = 1L
        val step = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "TOPIC_TREE_GENERATION",
            stepOrder = 0,
            systemPrompt = PromptEntity(PromptType.SYSTEM, "$pipelineName-sys", "System {{topicName}} {{coverageArea}} {{parentChain}} {{siblingTopics}} {{depth}} {{maxDepth}} {{parentKey}} {{topicKey}}"),
            userPrompt = PromptEntity(PromptType.USER, "$pipelineName-usr", "User {{topicName}} {{coverageArea}} {{parentChain}} {{siblingTopics}} {{depth}} {{maxDepth}} {{parentKey}} {{topicKey}}")
        )
        step.id = 1L
        pipeline.steps.add(step)
        
        `when`(repository.findById(1L)).thenReturn(pipeline)
        
        return pipeline
    }

    @Test
    fun `should return correct step type`() {
        assertEquals("TOPIC_TREE_GENERATION", service.getStepType())
    }

    @Test
    fun `should throw exception if root topic not found`() {
        val pipeline = createPipelineWithStep("test-pipeline", "nonexistent-topic")
        `when`(questionCatalogClient.findTopic("nonexistent-topic")).thenReturn(null)
        val step = pipeline.steps.first()
        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }
    @Test
    fun `should generate topic tree with leaf topics only at depth 1`() {
        val pipelineName = "java-pipeline"
        val topicKey = "java"
        val pipeline = createPipelineWithStep(pipelineName, topicKey)
        val rootTopic = TopicClientResponse(
            key = "java",
            name = "Java",
            path = "/java",
            coverageArea = "The Java programming language"
        )
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(rootTopic)
        // All subtopics are leaves — no further decomposition
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            topics:
              - key: "java-basics"
                name: "Java Basics"
                parentTopicKey: "java"
                coverageArea: "Fundamentals of Java"
                leaf: true
              - key: "java-oop"
                name: "Object-Oriented Programming"
                parentTopicKey: "java"
                coverageArea: "OOP concepts in Java"
                leaf: true
        """.trimIndent())
        val step = pipeline.steps.first()
        service.generate(step)
        verify(repository).save(pipeline)
        verify(artifactStorage, atLeastOnce()).saveTopicTreeArtifact(eq(topicKey) ?: "", eq(pipelineName) ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.WAITING_ARTIFACT_APPROVAL, pipeline.status)
        val artifact = step.artifact as? TopicTreeArtifactEntity
        assertTrue(artifact != null)
        // Root node + 2 leaf subtopics = 3
        assertEquals(3, artifact!!.nodes.size)
        val leafNodes = artifact.nodes.filter { it.leaf }
        assertEquals(2, leafNodes.size)
        val rootNodes = artifact.nodes.filter { it.depth == 0 }
        assertEquals(1, rootNodes.size)
        assertEquals("java", rootNodes.first().key)
    }
    @Test
    fun `should recursively decompose non-leaf topics`() {
        val pipelineName = "java-deep"
        val topicKey = "java"
        val pipeline = createPipelineWithStep(pipelineName, topicKey)
        val rootTopic = TopicClientResponse(
            key = "java",
            name = "Java",
            path = "/java",
            coverageArea = "The Java programming language"
        )
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(rootTopic)
        // First call: root decomposition — one non-leaf subtopic
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: ""))
            .thenReturn("""
                topics:
                  - key: "java-basics"
                    name: "Java Basics"
                    parentTopicKey: "java"
                    coverageArea: "Fundamentals of Java"
                    leaf: false
            """.trimIndent())
            // Second call: decompose java-basics — all leaves
            .thenReturn("""
                topics:
                  - key: "java-variables"
                    name: "Variables"
                    parentTopicKey: "java-basics"
                    coverageArea: "Variable declarations and types"
                    leaf: true
                  - key: "java-operators"
                    name: "Operators"
                    parentTopicKey: "java-basics"
                    coverageArea: "Operators in Java"
                    leaf: true
            """.trimIndent())
        val step = pipeline.steps.first()
        service.generate(step)
        val artifact = step.artifact as? TopicTreeArtifactEntity
        assertTrue(artifact != null)
        // Root(java) + java-basics + java-variables + java-operators = 4
        assertEquals(4, artifact!!.nodes.size)
        val depth0 = artifact.nodes.filter { it.depth == 0 }
        assertEquals(1, depth0.size)
        assertEquals("java", depth0.first().key)
        val depth1 = artifact.nodes.filter { it.depth == 1 }
        assertEquals(1, depth1.size)
        assertEquals("java-basics", depth1.first().key)
        val depth2 = artifact.nodes.filter { it.depth == 2 }
        assertEquals(2, depth2.size)
        assertTrue(depth2.all { it.leaf })
    }
    @Test
    fun `should mark topics as leaf when max depth is reached`() {
        val pipelineName = "shallow-pipeline"
        val topicKey = "java"
        val pipeline = createPipelineWithStep(pipelineName, topicKey)
        val rootTopic = TopicClientResponse(
            key = "java",
            name = "Java",
            path = "/java",
            coverageArea = "The Java programming language"
        )
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(rootTopic)
        // Depth 0→1: non-leaf
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: ""))
            .thenReturn("""
                topics:
                  - key: "java-basics"
                    name: "Java Basics"
                    parentTopicKey: "java"
                    coverageArea: "Fundamentals"
                    leaf: false
            """.trimIndent())
            // Depth 1→2: still non-leaf
            .thenReturn("""
                topics:
                  - key: "java-variables"
                    name: "Variables"
                    parentTopicKey: "java-basics"
                    coverageArea: "Variable declarations"
                    leaf: false
            """.trimIndent())
            // Depth 2→3: non-leaf but will be forced to leaf by max depth
            .thenReturn("""
                topics:
                  - key: "java-local-vars"
                    name: "Local Variables"
                    parentTopicKey: "java-variables"
                    coverageArea: "Local variable declarations"
                    leaf: false
            """.trimIndent())
        val step = pipeline.steps.first()
        service.generate(step)
        val artifact = step.artifact as? TopicTreeArtifactEntity
        assertTrue(artifact != null)
        // java-local-vars is at depth 3 which equals DEFAULT_MAX_DEPTH, so it should be leaf
        val deepestNodes = artifact!!.nodes.filter { it.key == "java-local-vars" }
        assertEquals(1, deepestNodes.size)
        assertTrue(deepestNodes.first().leaf)
    }
    @Test
    fun `should handle empty LLM response gracefully`() {
        val pipelineName = "empty-response"
        val topicKey = "java"
        val pipeline = createPipelineWithStep(pipelineName, topicKey)
        val rootTopic = TopicClientResponse(
            key = "java",
            name = "Java",
            path = "/java",
            coverageArea = "The Java programming language"
        )
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(rootTopic)
        // LLM returns no topics
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            topics: []
        """.trimIndent())
        val step = pipeline.steps.first()
        service.generate(step)
        val artifact = step.artifact as? TopicTreeArtifactEntity
        assertTrue(artifact != null)
        // Root should become a leaf since no subtopics were generated
        assertEquals(1, artifact!!.nodes.size)
        assertTrue(artifact.nodes.first().leaf)
        assertEquals("java", artifact.nodes.first().key)
    }
    @Test
    fun `should set artifact status to PENDING_FOR_APPROVAL`() {
        val pipelineName = "status-test"
        val topicKey = "java"
        val pipeline = createPipelineWithStep(pipelineName, topicKey)
        val rootTopic = TopicClientResponse(
            key = "java",
            name = "Java",
            path = "/java",
            coverageArea = "The Java programming language"
        )
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(rootTopic)
        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            topics:
              - key: "java-basics"
                name: "Java Basics"
                parentTopicKey: "java"
                coverageArea: "Fundamentals"
                leaf: true
        """.trimIndent())
        val step = pipeline.steps.first()
        service.generate(step)
        val artifact = step.artifact as? TopicTreeArtifactEntity
        assertEquals(ArtifactStatus.PENDING_FOR_APPROVAL, artifact?.status)
    }
}
