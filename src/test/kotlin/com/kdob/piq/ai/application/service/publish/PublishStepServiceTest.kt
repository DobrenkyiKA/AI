package com.kdob.piq.ai.application.service.publish

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.*
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*

class PublishStepServiceTest {

    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val service = PublishStepService(repository, artifactStorage, questionCatalogClient)

    private fun createFullPipeline(
        name: String,
        topicKey: String,
        shortAnswersStatus: ArtifactStatus = ArtifactStatus.APPROVED
    ): PipelineEntity {
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)

        // Topic tree step with artifact
        val topicTreeStep = PipelineStepEntity(pipeline = pipeline, stepType = "TOPIC_TREE_GENERATION", stepOrder = 0)
        val topicTreeArtifact = TopicTreeArtifactEntity(pipeline = pipeline, maxDepth = 3)
        topicTreeArtifact.status = ArtifactStatus.APPROVED
        topicTreeArtifact.nodes.add(TopicTreeNodeEntity(
            key = "java-basics", name = "Java Basics", parentTopicKey = topicKey,
            coverageArea = "Fundamentals", depth = 1, leaf = true, topicTreeArtifact = topicTreeArtifact
        ))
        topicTreeStep.artifact = topicTreeArtifact
        pipeline.steps.add(topicTreeStep)

        // Questions step
        pipeline.steps.add(PipelineStepEntity(pipeline = pipeline, stepType = "QUESTIONS_GENERATION", stepOrder = 1))

        // Answers step
        pipeline.steps.add(PipelineStepEntity(pipeline = pipeline, stepType = "LONG_ANSWERS_GENERATION", stepOrder = 2))

        // Short answers step with artifact
        val shortAnswersStep = PipelineStepEntity(pipeline = pipeline, stepType = "SHORT_ANSWERS_GENERATION", stepOrder = 3)
        val shortAnswersArtifact = AnswersArtifactEntity(pipeline = pipeline)
        shortAnswersArtifact.status = shortAnswersStatus
        shortAnswersStep.artifact = shortAnswersArtifact
        pipeline.steps.add(shortAnswersStep)

        // Publish step
        pipeline.steps.add(PipelineStepEntity(pipeline = pipeline, stepType = "PUBLISH", stepOrder = 4))

        return pipeline
    }

    @Test
    fun `should return correct step type`() {
        assertEquals("PUBLISH", service.getStepType())
    }

    @Test
    fun `should throw exception if short answers step not found`() {
        val pipeline = PipelineEntity(name = "test", topicKey = "java")
        val step = PipelineStepEntity(pipeline = pipeline, stepType = "PUBLISH", stepOrder = 0)
        pipeline.steps.add(step)

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should throw exception if short answers artifact is not approved`() {
        val pipeline = createFullPipeline("test", "java", ArtifactStatus.PENDING_FOR_APPROVAL)
        val step = pipeline.steps.find { it.stepType == "PUBLISH" }!!

        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }

    @Test
    fun `should publish topic tree and set status to PUBLISHED`() {
        val pipeline = createFullPipeline("java-pipeline", "java")

        val rootTopic = TopicClientResponse(key = "java", name = "Java", path = "/java", coverageArea = "Java")
        `when`(questionCatalogClient.findTopic("java")).thenReturn(rootTopic)

        val expectedRequest = CreateTopicClientRequest(
            key = "java-basics", name = "Java Basics", parentPath = "/java",
            coverageArea = "Fundamentals", exclusions = ""
        )
        val createdTopic = TopicClientResponse(key = "java-basics", name = "Java Basics", path = "/java/java-basics")
        `when`(questionCatalogClient.createTopic(expectedRequest)).thenReturn(createdTopic)

        val step = pipeline.steps.find { it.stepType == "PUBLISH" }!!
        service.generate(step)

        verify(questionCatalogClient).findTopic("java")
        verify(questionCatalogClient).createTopic(expectedRequest)
        verify(repository).save(pipeline)
        assertEquals(PipelineStatus.PUBLISHED, pipeline.status)
    }

    @Test
    fun `should throw exception if root topic not found in catalog`() {
        val pipeline = createFullPipeline("java-pipeline", "java")
        `when`(questionCatalogClient.findTopic("java")).thenReturn(null)

        val step = pipeline.steps.find { it.stepType == "PUBLISH" }!!
        assertThrows<IllegalStateException> {
            service.generate(step)
        }
    }
}
