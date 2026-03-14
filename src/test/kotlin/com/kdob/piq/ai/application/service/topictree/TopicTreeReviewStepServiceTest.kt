package com.kdob.piq.ai.application.service.topictree

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class TopicTreeReviewStepServiceTest {

    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val service = TopicTreeReviewStepService(repository, artifactStorage)

    private fun createPipelineWithSteps(pipelineName: String, topicKey: String): PipelineEntity {
        val pipeline = PipelineEntity(name = pipelineName, topicKey = topicKey)
        pipeline.steps.add(
            PipelineStepEntity(
                pipeline = pipeline,
                stepType = "TOPIC_TREE_GENERATION",
                stepOrder = 0,
                systemPrompt = PromptEntity(PromptType.SYSTEM, "$pipelineName-gen-sys", "System prompt"),
                userPrompt = PromptEntity(PromptType.USER, "$pipelineName-gen-usr", "User prompt")
            )
        )
        pipeline.steps.add(
            PipelineStepEntity(
                pipeline = pipeline,
                stepType = "TOPIC_TREE_REVIEW",
                stepOrder = 1,
                systemPrompt = null,
                userPrompt = null
            )
        )
        return pipeline
    }

    private fun createTopicTreeArtifact(pipeline: PipelineEntity, status: ArtifactStatus): TopicTreeArtifactEntity {
        val artifact = TopicTreeArtifactEntity(pipeline = pipeline, maxDepth = 3)
        artifact.status = status
        artifact.nodes.add(
            TopicTreeNodeEntity(
                topicTreeArtifact = artifact,
                key = "java",
                name = "Java",
                parentTopicKey = null,
                coverageArea = "The Java programming language",
                depth = 0,
                leaf = false
            )
        )
        artifact.nodes.add(
            TopicTreeNodeEntity(
                topicTreeArtifact = artifact,
                key = "java-basics",
                name = "Java Basics",
                parentTopicKey = "java",
                coverageArea = "Fundamentals of Java",
                depth = 1,
                leaf = true
            )
        )
        return artifact
    }

    @Test
    fun `should return correct step type`() {
        assertEquals("TOPIC_TREE_REVIEW", service.getStepType())
    }

    @Test
    fun `should advance pipeline status when topic tree artifact is approved`() {
        val pipeline = createPipelineWithSteps("java-pipeline", "java")
        val artifact = createTopicTreeArtifact(pipeline, ArtifactStatus.APPROVED)
        pipeline.steps[0].artifact = artifact

        val reviewStep = pipeline.steps[1]
        service.generate(reviewStep)

        assertEquals(PipelineStatus.TOPIC_TREE_APPROVED, pipeline.status)
        verify(repository).save(pipeline)
    }

    @Test
    fun `should throw exception when topic tree artifact is pending approval`() {
        val pipeline = createPipelineWithSteps("java-pipeline", "java")
        val artifact = createTopicTreeArtifact(pipeline, ArtifactStatus.PENDING_FOR_APPROVAL)
        pipeline.steps[0].artifact = artifact

        val reviewStep = pipeline.steps[1]
        val exception = assertThrows<IllegalStateException> {
            service.generate(reviewStep)
        }

        assertEquals(
            "Topic tree artifact is pending approval in pipeline 'java-pipeline'. " +
                "Please review and approve the topic tree before proceeding.",
            exception.message
        )
    }

    @Test
    fun `should throw exception when topic tree artifact is marked for regeneration`() {
        val pipeline = createPipelineWithSteps("java-pipeline", "java")
        val artifact = createTopicTreeArtifact(pipeline, ArtifactStatus.TO_BE_REGENERATED)
        pipeline.steps[0].artifact = artifact

        val reviewStep = pipeline.steps[1]
        val exception = assertThrows<IllegalStateException> {
            service.generate(reviewStep)
        }

        assertEquals(
            "Topic tree artifact is marked for regeneration in pipeline 'java-pipeline'. " +
                "Please re-run TOPIC_TREE_GENERATION step first.",
            exception.message
        )
    }

    @Test
    fun `should throw exception when TOPIC_TREE_GENERATION step is missing`() {
        val pipeline = PipelineEntity(name = "broken-pipeline", topicKey = "java")
        pipeline.steps.add(
            PipelineStepEntity(
                pipeline = pipeline,
                stepType = "TOPIC_TREE_REVIEW",
                stepOrder = 0,
                systemPrompt = null,
                userPrompt = null
            )
        )

        val reviewStep = pipeline.steps[0]
        val exception = assertThrows<IllegalStateException> {
            service.generate(reviewStep)
        }

        assertEquals(
            "TOPIC_TREE_GENERATION step not found in pipeline 'broken-pipeline'",
            exception.message
        )
    }

    @Test
    fun `should throw exception when topic tree artifact does not exist`() {
        val pipeline = createPipelineWithSteps("java-pipeline", "java")
        // Don't set artifact on the generation step

        val reviewStep = pipeline.steps[1]
        val exception = assertThrows<IllegalStateException> {
            service.generate(reviewStep)
        }

        assertEquals(
            "Topic tree artifact not found in pipeline 'java-pipeline'. Run TOPIC_TREE_GENERATION first.",
            exception.message
        )
    }
}
