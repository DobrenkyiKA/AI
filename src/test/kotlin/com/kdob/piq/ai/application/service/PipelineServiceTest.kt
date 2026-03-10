package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.topics.TopicsGenerationService
import com.kdob.piq.ai.application.service.questions.QuestionsGenerationService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*

class PipelineServiceTest {

    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val topicsGenerationService = mock(TopicsGenerationService::class.java)
    private val questionsGenerationService = mock(QuestionsGenerationService::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val service = PipelineService(
        repository,
        artifactStorage,
        listOf(topicsGenerationService, questionsGenerationService),
        questionCatalogClient
    )

    @BeforeEach
    fun setup() {
        `when`(topicsGenerationService.getStepType()).thenReturn("TOPICS_GENERATION")
        `when`(questionsGenerationService.getStepType()).thenReturn("QUESTIONS_GENERATION")
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
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(artifactStorage.loadArtifact(name, "TOPICS_GENERATION")).thenReturn(expectedYaml)

        val result = service.getArtifact(name, 0)

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
        existingEntity.steps.add(PipelineStepEntity(existingEntity, "TOPICS_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(existingEntity)
        `when`(repository.save(existingEntity)).thenReturn(existingEntity)
        `when`(repository.saveAndFlush(existingEntity)).thenReturn(existingEntity)

        service.updatePipeline(name, yamlContent)

        assert(existingEntity.topicsArtifact?.topics?.size == 1)
        assert(existingEntity.topicsArtifact?.topics?.first()?.key == "java-gc-v2")
        verify(artifactStorage).saveTopicsArtifact(name, yamlContent)
        verify(repository).save(existingEntity)
    }

    @Test
    fun `should run topics generation step`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runStep(name, 0)

        verify(topicsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run questions generation step`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runStep(name, 1)

        verify(questionsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run pipeline from topics generation`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runPipelineFrom(name, 0)

        verify(topicsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
        verify(questionsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run pipeline from questions generation`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runPipelineFrom(name, 1)

        verify(questionsGenerationService).generate(eq(pipeline) ?: pipeline, any(PipelineStepEntity::class.java))
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
    fun `should publish approved topics artifact`() {
        val name = "java-core-interview-v1"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)
        val topicsArtifact = TopicsArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.APPROVED

        val child1 = PipelineTopicEntity(
            key = "java-fundamentals",
            name = "Java Fundamentals",
            parentTopicKey = topicKey,
            coverageArea = "Basics",
            topicsArtifact = topicsArtifact
        )
        val child2 = PipelineTopicEntity(
            key = "java-collections",
            name = "Java Collections",
            parentTopicKey = "java-fundamentals",
            coverageArea = "Collections",
            topicsArtifact = topicsArtifact
        )
        topicsArtifact.topics.add(child1)
        topicsArtifact.topics.add(child2)
        pipeline.topicsArtifact = topicsArtifact

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

        service.publishTopicsArtifact(name)

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
    fun `should fail to publish if topics artifact is not approved`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        val topicsArtifact = TopicsArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        pipeline.topicsArtifact = topicsArtifact

        `when`(repository.findByName(name)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.publishTopicsArtifact(name)
        }
    }
}
