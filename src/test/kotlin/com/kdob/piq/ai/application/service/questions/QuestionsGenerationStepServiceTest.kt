package com.kdob.piq.ai.application.service.questions

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

class QuestionsGenerationStepServiceTest {

    private val generator = mock(OpenAiChatService::class.java)
    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val service = QuestionsGenerationStepService(generator, repository, artifactStorage)

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
            systemPrompt = PromptEntity(PromptType.SYSTEM, "$name-sys", "System {{topicKey}} {{topicName}} {{coverageArea}} {{topicType}} {{childTopicsList}} {{parentChain}}"),
            userPrompt = PromptEntity(PromptType.USER, "$name-usr", "User {{topicKey}} {{topicName}} {{coverageArea}} {{topicType}} {{childTopicsList}} {{parentChain}}")
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
        val step = PipelineStepEntity(
            pipeline = pipeline,
            stepType = "QUESTIONS_GENERATION",
            stepOrder = 0
        )
        pipeline.steps.add(step)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if topic tree artifact is not approved`() {
        val pipeline = createPipeline("test", "java")
        val topicTreeArtifact = createTopicTreeArtifact(pipeline, ArtifactStatus.PENDING_FOR_APPROVAL)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should generate questions for all topics in the tree`() {
        val pipeline = createPipeline("java-pipeline", "java")
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - text: "What is Java?"
                level: "junior"
              - text: "Explain JVM internals"
                level: "senior"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        service.generate(step)

        verify(repository).save(pipeline)
        verify(artifactStorage).saveQuestionsArtifact(eq("java") ?: "", eq("java-pipeline") ?: "", anyString() ?: "")
        assertEquals(PipelineStatus.WAITING_ARTIFACT_APPROVAL, pipeline.status)

        val artifact = step.artifact as? AnswersArtifactEntity
        assertNotNull(artifact)
        assertEquals(2, artifact!!.topicsWithQA.size) // 2 topics: java, java-basics
        assertTrue(artifact.topicsWithQA.all { it.entries.size == 2 })
        assertTrue(artifact.topicsWithQA.flatMap { it.entries }.all { it.answer == null })
        assertTrue(artifact.topicsWithQA.flatMap { it.entries }.all { it.shortAnswer == null })
    }

    @Test
    fun `should parse questions with levels correctly`() {
        val pipeline = createPipeline("java-pipeline", "java")
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - text: "Basic question"
                level: "junior"
              - text: "Advanced question"
                level: "principal"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val allEntries = artifact!!.topicsWithQA.flatMap { it.entries }
        assertTrue(allEntries.any { it.level == "junior" })
        assertTrue(allEntries.any { it.level == "principal" })
    }

    @Test
    fun `should handle simple string questions with default level`() {
        val pipeline = createPipeline("java-pipeline", "java")
        val topicTreeArtifact = createTopicTreeArtifact(pipeline)
        pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!.artifact = topicTreeArtifact

        `when`(generator.executePrompt(anyString() ?: "", anyString() ?: "")).thenReturn("""
            questions:
              - "What is Java?"
              - "Explain polymorphism"
        """.trimIndent())

        val step = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
        service.generate(step)

        val artifact = step.artifact as? AnswersArtifactEntity
        val allEntries = artifact!!.topicsWithQA.flatMap { it.entries }
        assertTrue(allEntries.all { it.level == "mid" }) // default level
    }
}
