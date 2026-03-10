package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.step0.Step0TopicsGenerationService
import com.kdob.piq.ai.application.service.step1.Step1QuestionGenerationService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineStepRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*

class PipelineServiceTest {

    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val step0TopicsGenerationService = mock(Step0TopicsGenerationService::class.java)
    private val step1QuestionGenerationService = mock(Step1QuestionGenerationService::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val service = PipelineService(
        repository,
        artifactStorage,
        listOf(step0TopicsGenerationService, step1QuestionGenerationService),
        questionCatalogClient
    )

    @BeforeEach
    fun setup() {
        `when`(step0TopicsGenerationService.getStepType()).thenReturn("TOPICS_GENERATION")
        `when`(step1QuestionGenerationService.getStepType()).thenReturn("QUESTIONS_GENERATION")
    }
    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    @Test
    fun `should create pipeline with normalized name`() {
        val name = "Java Core Interview v1"
        val topicKey = "java-core"
        val expectedNormalized = "java-core-interview-v1"

        `when`(repository.findByName(expectedNormalized)).thenReturn(null)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        val result = service.createPipeline(name, topicKey, emptyList())

        assertEquals(expectedNormalized, result.pipelineName)
        assertEquals(topicKey, result.topicKey)
        verify(repository).save(any(PipelineEntity::class.java))
    }

    private fun <T> any(type: Class<T>): T {
        org.mockito.Mockito.any(type)
        return if (type == PipelineEntity::class.java) {
            PipelineEntity(name = "dummy", topicKey = "dummy-topic") as T
        } else {
            @Suppress("UNCHECKED_CAST")
            null as T
        }
    }

    @Test
    fun `should fail to create pipeline with invalid characters`() {
        val name = "Java Core @ Interview"

        assertThrows<IllegalArgumentException> {
            service.createPipeline(name, "java-core", emptyList())
        }
    }

    @Test
    fun `should fail to create pipeline with existing name`() {
        val name = "existing-pipeline"
        `when`(repository.findByName(name)).thenReturn(PipelineEntity(name = name, topicKey = "some-topic"))

        assertThrows<IllegalArgumentException> {
            service.createPipeline(name, "some-topic", emptyList())
        }
    }

    @Test
    fun `should delete pipeline and its artifacts`() {
        val name = "java-core-interview-v1"

        service.deletePipeline(name)

        verify(repository).deleteByName(name)
        verify(artifactStorage).deleteArtifacts(name)
    }

    @Test
    fun `should load pipeline artifact`() {
        val name = "java-core-interview-v1"
        val expectedYaml = "topics: []"
        `when`(artifactStorage.loadArtifact(name, 0)).thenReturn(expectedYaml)

        val result = service.getPipelineArtifact(name)

        assertEquals(expectedYaml, result)
    }

    @Test
    fun `should update pipeline and its artifacts`() {
        val name = "java-core-interview-v1"
        val yamlContent = """
              topics:
                - key: java-gc-v2
                  name: JVM Garbage Collection v2
                  coverageArea: Updated memory management
        """.trimIndent()

        val existingEntity =
            com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity(name = name, topicKey = "java-core")
        `when`(repository.findByName(name)).thenReturn(existingEntity)
        `when`(repository.save(existingEntity)).thenReturn(existingEntity)
        `when`(repository.saveAndFlush(existingEntity)).thenReturn(existingEntity)

        service.updatePipeline(name, yamlContent)

        assert(existingEntity.artifactStep0?.topics?.size == 1)
        assert(existingEntity.artifactStep0?.topics?.first()?.key == "java-gc-v2")
        verify(artifactStorage).saveArtifact(name, 0, yamlContent)
        verify(repository).save(existingEntity)
    }

    @Test
    fun `should run step 0`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runStep(name, 0)

        verify(step0TopicsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run step 1`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runStep(name, 1)

        verify(step1QuestionGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run pipeline from step 0`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runPipelineFrom(name, 0)

        verify(step0TopicsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
        verify(step1QuestionGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run pipeline from step 1`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runPipelineFrom(name, 1)

        verify(step1QuestionGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should fail for unsupported step run`() {
        val name = "java-core-interview-v1"
        `when`(repository.findByName(name)).thenReturn(PipelineEntity(name = name, topicKey = "java-core"))

        assertThrows<IllegalArgumentException> {
            service.runStep(name, 99)
        }
    }

    @Test
    fun `should publish approved step 0 artifact`() {
        val name = "java-core-interview-v1"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)
        val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.APPROVED

        val child1 = Step0TopicEntity(
            key = "java-fundamentals",
            name = "Java Fundamentals",
            parentTopicKey = topicKey,
            coverageArea = "Basics",
            artifactStep0 = artifactStep0
        )
        val child2 = Step0TopicEntity(
            key = "java-collections",
            name = "Java Collections",
            parentTopicKey = "java-fundamentals",
            coverageArea = "Collections",
            artifactStep0 = artifactStep0
        )
        artifactStep0.topics.add(child1)
        artifactStep0.topics.add(child2)
        pipeline.artifactStep0 = artifactStep0

        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(questionCatalogClient.findTopic(topicKey)).thenReturn(
            TopicClientResponse(
                topicKey,
                "Java Core",
                "/java-core"
            )
        )
        `when`(questionCatalogClient.createTopic(any(CreateTopicClientRequest::class.java))).thenAnswer {
            val req = it.arguments[0] as CreateTopicClientRequest
            TopicClientResponse(req.key, req.name, "${req.parentPath}/${req.key}")
        }

        service.publishStep0Artifact(name)

        verify(questionCatalogClient).createTopic(
            CreateTopicClientRequest(
                "java-fundamentals",
                "Java Fundamentals",
                "/java-core",
                "Basics",
                ""
            )
        )
        verify(questionCatalogClient).createTopic(
            CreateTopicClientRequest(
                "java-collections",
                "Java Collections",
                "/java-core/java-fundamentals",
                "Collections",
                ""
            )
        )
    }

    @Test
    fun `should fail to publish if step 0 artifact is not approved`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.PENDING_FOR_APPROVAL
        pipeline.artifactStep0 = artifactStep0

        `when`(repository.findByName(name)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.publishStep0Artifact(name)
        }
    }
}
