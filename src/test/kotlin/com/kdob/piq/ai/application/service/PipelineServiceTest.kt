package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.topics.TopicPipelineStepService
import com.kdob.piq.ai.application.service.questions.QuestionPipelineStepService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.domain.repository.PromptRepository
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
    private val promptRepository = mock(PromptRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val topicsGenerationService = mock(TopicPipelineStepService::class.java)
    private val questionsGenerationService = mock(QuestionPipelineStepService::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val service = PipelineService(
        repository,
        promptRepository,
        artifactStorage,
        listOf(topicsGenerationService, questionsGenerationService),
        questionCatalogClient
    )

    @BeforeEach
    fun setup() {
        `when`(topicsGenerationService.getStepType()).thenReturn("TOPICS_GENERATION")
        `when`(questionsGenerationService.getStepType()).thenReturn("QUESTIONS_GENERATION")
        `when`(promptRepository.save(any(PromptEntity::class.java))).thenAnswer { it.arguments[0] as PromptEntity }
        `when`(promptRepository.findByName(org.mockito.ArgumentMatchers.anyString())).thenAnswer { invocation ->
            val name = invocation.arguments[0] as String
            if (name.startsWith("DEFAULT_")) {
                val type = if (name.endsWith("SYSTEM")) PromptType.SYSTEM else PromptType.USER
                PromptEntity(type = type, name = name, content = "default content")
            } else {
                null
            }
        }
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
        } else if (type == PromptEntity::class.java) {
            PromptEntity(type = PromptType.SYSTEM, name = "dummy", content = "dummy") as T
        } else {
            @Suppress("UNCHECKED_CAST")
            null as T
        }
    }

    @Test
    fun `should create pipeline with steps and use default prompts`() {
        val name = "test-pipeline"
        val topicKey = "java-core"
        val stepRequest = com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest(
            type = "TOPICS_GENERATION"
        )

        `when`(repository.findByName(name)).thenReturn(null)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        val result = service.createPipeline(name, topicKey, listOf(stepRequest))

        assertEquals(1, result.steps.size)
        val step = result.steps[0]
        assertEquals("TOPICS_GENERATION", step.type)
        assertEquals("DEFAULT_TOPICS_GENERATION_SYSTEM", step.systemPromptName)
        assertEquals("DEFAULT_TOPICS_GENERATION_USER", step.userPromptName)
        assertEquals("default content", step.systemPrompt)
    }

    @Test
    fun `should update shared default prompt when provided name and content`() {
        val name = "test-pipeline"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        val defaultPrompt = PromptEntity(type = PromptType.SYSTEM, name = "DEFAULT_TOPICS_GENERATION_SYSTEM", content = "original content")
        `when`(promptRepository.findByName("DEFAULT_TOPICS_GENERATION_SYSTEM")).thenReturn(defaultPrompt)

        val updateRequest = com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineStepRequest(
            type = "TOPICS_GENERATION",
            systemPromptName = "DEFAULT_TOPICS_GENERATION_SYSTEM",
            systemPrompt = "new improved content"
        )

        service.updatePipelineMetadata(name, null, listOf(updateRequest))

        assertEquals("new improved content", defaultPrompt.content)
        verify(promptRepository).save(defaultPrompt)
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

        val topicsStep = existingEntity.steps.find { it.stepType == "TOPICS_GENERATION" }
        val topicsArtifact = topicsStep?.artifact as? TopicsPipelineArtifactEntity
        assert(topicsArtifact?.topics?.size == 1)
        assert(topicsArtifact?.topics?.first()?.key == "java-gc-v2")
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
        val topicsArtifact = TopicsPipelineArtifactEntity(pipeline = pipeline)
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
        val step = PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0)
        step.artifact = topicsArtifact
        pipeline.steps.add(step)

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
        val topicsArtifact = TopicsPipelineArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        val step = PipelineStepEntity(pipeline, "TOPICS_GENERATION", 0)
        step.artifact = topicsArtifact
        pipeline.steps.add(step)

        `when`(repository.findByName(name)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.publishTopicsArtifact(name)
        }
    }
}
