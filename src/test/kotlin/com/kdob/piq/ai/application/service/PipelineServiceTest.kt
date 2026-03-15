package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.service.topictree.TopicTreeGenerationStepService
import com.kdob.piq.ai.application.service.questions.QuestionsGenerationStepService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
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
    private val topicTreeGenerationService = mock(TopicTreeGenerationStepService::class.java)
    private val questionsGenerationService = mock(QuestionsGenerationStepService::class.java)
    private val questionCatalogClient = mock(QuestionCatalogClient::class.java)
    private val statusService = mock(PipelineStatusService::class.java)
    private val generationLogRepository = mock(GenerationLogRepository::class.java)
    private val service = PipelineService(
        repository,
        promptRepository,
        artifactStorage,
        listOf(topicTreeGenerationService, questionsGenerationService),
        questionCatalogClient,
        statusService,
        generationLogRepository
    )

    @BeforeEach
    fun setup() {
        `when`(topicTreeGenerationService.getStepType()).thenReturn("TOPIC_TREE_GENERATION")
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
        } else if (type == CreateTopicClientRequest::class.java) {
            CreateTopicClientRequest(key = "", name = "", parentPath = "") as T
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
            type = "TOPIC_TREE_GENERATION"
        )

        `when`(repository.findByName(name)).thenReturn(null)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        val result = service.createPipeline(name, topicKey, listOf(stepRequest))

        assertEquals(1, result.steps.size)
        val step = result.steps[0]
        assertEquals("TOPIC_TREE_GENERATION", step.type)
        assertEquals("DEFAULT_TOPIC_TREE_GENERATION_SYSTEM", step.systemPromptName)
        assertEquals("DEFAULT_TOPIC_TREE_GENERATION_USER", step.userPromptName)
        assertEquals("default content", step.systemPrompt)
    }

    @Test
    fun `should update shared default prompt when provided name and content`() {
        val name = "test-pipeline"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        val defaultPrompt = PromptEntity(type = PromptType.SYSTEM, name = "DEFAULT_TOPIC_TREE_GENERATION_SYSTEM", content = "original content")
        `when`(promptRepository.findByName("DEFAULT_TOPIC_TREE_GENERATION_SYSTEM")).thenReturn(defaultPrompt)

        val updateRequest = com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineStepRequest(
            type = "TOPIC_TREE_GENERATION",
            systemPromptName = "DEFAULT_TOPIC_TREE_GENERATION_SYSTEM",
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
        `when`(repository.findByName(name)).thenReturn(PipelineEntity(name = name, topicKey = "java-core"))

        service.deletePipeline(name)

        verify(repository).deleteByName(name)
        verify(artifactStorage).deleteArtifacts("java-core", name)
    }

    @Test
    fun `should load pipeline artifact`() {
        val name = "java-core-interview-v1"
        val expectedYaml = "topics: []"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(artifactStorage.loadArtifact("java-core", name, "TOPIC_TREE_GENERATION")).thenReturn(expectedYaml)

        val result = service.getArtifact(name, 0)

        assertEquals(expectedYaml, result)
    }


    @Test
    fun `should runStep update status to GENERATION_IN_PROGRESS before running step`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)

        service.runStep(name, 0)

        verify(statusService).updateStatus(name, PipelineStatus.GENERATION_IN_PROGRESS)
        verify(topicTreeGenerationService).generate(any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should set FAILED status when step throws exception`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0))
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)
        doThrow(RuntimeException("AI service error")).`when`(topicTreeGenerationService)
            .generate(any(PipelineStepEntity::class.java))

        assertThrows<RuntimeException> {
            service.runStep(name, 0)
        }

        verify(statusService).updateStatus(name, PipelineStatus.FAILED)
    }

    @Test
    fun `should run questions generation step`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)

        service.runStep(name, 1)

        verify(questionsGenerationService).generate(any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run pipeline from topic tree generation and stop when artifact not approved`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        val step0 = PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0)
        pipeline.steps.add(step0)
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)
        `when`(repository.findByName(name)).thenReturn(pipeline)

        val topicTreeArtifact = TopicTreeArtifactEntity(pipeline = pipeline)
        topicTreeArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        doAnswer {
            step0.artifact = topicTreeArtifact
            null
        }.`when`(topicTreeGenerationService).generate(any(PipelineStepEntity::class.java))

        service.runPipelineFrom(name, 0)

        verify(topicTreeGenerationService).generate(any(PipelineStepEntity::class.java))
        verify(questionsGenerationService, never()).generate(any(PipelineStepEntity::class.java))
        verify(statusService).updateStatus(name, PipelineStatus.WAITING_ARTIFACT_APPROVAL)
    }

    @Test
    fun `should run all steps when artifacts are approved`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        val step0 = PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0)
        pipeline.steps.add(step0)
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)
        `when`(repository.findByName(name)).thenReturn(pipeline)

        val topicTreeArtifact = TopicTreeArtifactEntity(pipeline = pipeline)
        topicTreeArtifact.status = ArtifactStatus.APPROVED
        
        // Mock generation setting artifact
        doAnswer {
            step0.artifact = topicTreeArtifact
            null
        }.`when`(topicTreeGenerationService).generate(any(PipelineStepEntity::class.java))

        service.runPipelineFrom(name, 0)

        verify(topicTreeGenerationService).generate(any(PipelineStepEntity::class.java))
        verify(questionsGenerationService).generate(any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should run pipeline from questions generation`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        pipeline.steps.add(PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0))
        pipeline.steps.add(PipelineStepEntity(pipeline, "QUESTIONS_GENERATION", 1))
        
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)
        `when`(repository.findByName(name)).thenReturn(pipeline)

        service.runPipelineFrom(name, 1)

        verify(questionsGenerationService).generate(any(PipelineStepEntity::class.java))
    }

    @Test
    fun `should fail for unsupported step run`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(statusService.getPipelineWithSteps(name)).thenReturn(pipeline)

        assertThrows<IllegalArgumentException> {
            service.runStep(name, 99)
        }
    }

    @Test
    fun `should publish approved topic tree artifact`() {
        val name = "java-core-interview-v1"
        val topicKey = "java-core"
        val pipeline = PipelineEntity(name = name, topicKey = topicKey)
        val topicTreeArtifact = TopicTreeArtifactEntity(pipeline = pipeline)
        topicTreeArtifact.status = ArtifactStatus.APPROVED

        val child1 = TopicTreeNodeEntity(
            key = "java-fundamentals",
            name = "Java Fundamentals",
            parentTopicKey = topicKey,
            coverageArea = "Basics",
            depth = 1,
            leaf = true,
            topicTreeArtifact = topicTreeArtifact
        )
        val child2 = TopicTreeNodeEntity(
            key = "java-collections",
            name = "Java Collections",
            parentTopicKey = "java-fundamentals",
            coverageArea = "Collections",
            depth = 2,
            leaf = true,
            topicTreeArtifact = topicTreeArtifact
        )
        topicTreeArtifact.nodes.add(child1)
        topicTreeArtifact.nodes.add(child2)
        val step = PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0)
        step.artifact = topicTreeArtifact
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

        service.publishArtifact(name)

        verify(questionCatalogClient).createTopic(
            CreateTopicClientRequest(
                "java-fundamentals",
                "Java Fundamentals",
                "/java-core",
                "Basics"
            )
        )
        verify(questionCatalogClient).createTopic(
            CreateTopicClientRequest(
                "java-collections",
                "Java Collections",
                "/java-core/java-fundamentals",
                "Collections"
            )
        )
    }

    @Test
    fun `should fail to publish if topic tree artifact is not approved`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        val topicTreeArtifact = TopicTreeArtifactEntity(pipeline = pipeline)
        topicTreeArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        val step = PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0)
        step.artifact = topicTreeArtifact
        pipeline.steps.add(step)

        `when`(repository.findByName(name)).thenReturn(pipeline)

        assertThrows<IllegalStateException> {
            service.publishArtifact(name)
        }
    }

    @Test
    fun `should update pipeline status to ARTIFACT_APPROVED when artifact is approved`() {
        val name = "java-core-interview-v1"
        val pipeline = PipelineEntity(name = name, topicKey = "java-core")
        val step = PipelineStepEntity(pipeline, "TOPIC_TREE_GENERATION", 0)
        val topicTreeArtifact = TopicTreeArtifactEntity(pipeline = pipeline)
        topicTreeArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        step.artifact = topicTreeArtifact
        pipeline.steps.add(step)
        pipeline.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL

        `when`(repository.findByName(name)).thenReturn(pipeline)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        service.updateArtifact(name, 0, "some yaml", ArtifactStatus.APPROVED)

        assertEquals(PipelineStatus.ARTIFACT_APPROVED, pipeline.status)
        assertEquals(ArtifactStatus.APPROVED, topicTreeArtifact.status)
    }
}
