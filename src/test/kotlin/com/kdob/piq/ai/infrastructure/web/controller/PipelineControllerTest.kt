package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PipelineControllerTest {

    private lateinit var mockMvc: MockMvc
    private val intakeService: PipelineService = mock(PipelineService::class.java)
    private val controller = PipelineController(intakeService)

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `should create pipeline and return 201 with Location header`() {
        val name = "test-pipeline"
        val topicKey = "test-topic"
        val entity = PipelineEntity(name = name, topicKey = topicKey)
        
        `when`(intakeService.createPipeline(name, topicKey)).thenReturn(entity)

        mockMvc.perform(post("/pipeline")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"$name\", \"topicKey\": \"$topicKey\"}"))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "http://localhost/pipeline/test-pipeline"))
            .andExpect(jsonPath("$.pipelineName").value(name))
            .andExpect(jsonPath("$.topicKey").value(topicKey))
    }

    @Test
    fun `should return 204 when deleting pipeline`() {
        val name = "test-pipeline"

        mockMvc.perform(delete("/pipeline/$name"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `should return 200 when getting all pipelines`() {
        val entity = PipelineEntity(name = "test-pipeline", topicKey = "test-topic")
        `when`(intakeService.findAll()).thenReturn(listOf(entity))

        mockMvc.perform(get("/pipeline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].pipelineName").value("test-pipeline"))
            .andExpect(jsonPath("$[0].topicKey").value("test-topic"))
    }

    @Test
    fun `should return 200 when getting single pipeline`() {
        val name = "test-pipeline"
        val entity = PipelineEntity(name = name, topicKey = "test-topic")
        `when`(intakeService.findByName(name)).thenReturn(entity)

        mockMvc.perform(get("/pipeline/$name"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pipelineName").value(name))
            .andExpect(jsonPath("$.topicKey").value("test-topic"))
    }

    @Test
    fun `should return artifact content`() {
        val name = "test-pipeline"
        val yaml = "pipeline: { name: 'test-pipeline' }"
        `when`(intakeService.getPipelineArtifact(name)).thenReturn(yaml)

        mockMvc.perform(get("/pipeline/$name/artifact"))
            .andExpect(status().isOk)
            .andExpect(content().string(yaml))
    }

    @Test
    fun `should return artifact by step`() {
        val name = "test-pipeline"
        val yaml = "step content"
        `when`(intakeService.getArtifact(name, 1)).thenReturn(yaml)

        mockMvc.perform(get("/pipeline/$name/artifact/1"))
            .andExpect(status().isOk)
            .andExpect(content().string(yaml))
    }

    @Test
    fun `should update artifact by step`() {
        val name = "test-pipeline"
        val yaml = "updated step content"
        val status = ArtifactStatus.APPROVED
        val entity = PipelineEntity(name = name, topicKey = "test-topic")
        
        `when`(intakeService.updateArtifact(name, 1, yaml, status)).thenReturn(entity)

        mockMvc.perform(put("/pipeline/$name/artifact/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\": \"$yaml\", \"status\": \"$status\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pipelineName").value(name))
    }

    @Test
    fun `should update pipeline and return updated response`() {
        val name = "test-pipeline"
        val yaml = "pipeline: { name: 'test-pipeline', topics: [] }"
        val entity = PipelineEntity(name = name, topicKey = "test-topic")
        `when`(intakeService.updatePipeline(name, yaml)).thenReturn(entity)

        mockMvc.perform(put("/pipeline/$name")
            .contentType(MediaType.TEXT_PLAIN)
            .content(yaml))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pipelineName").value(name))
    }

    @Test
    fun `should update pipeline metadata`() {
        val name = "test-pipeline"
        val newTopicKey = "new-topic"
        val entity = PipelineEntity(name = name, topicKey = newTopicKey)
        `when`(intakeService.updatePipelineMetadata(name, newTopicKey)).thenReturn(entity)

        mockMvc.perform(patch("/pipeline/$name")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"topicKey\": \"$newTopicKey\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topicKey").value(newTopicKey))
    }

    @Test
    fun `should run step and return updated response`() {
        val name = "test-pipeline"
        val step = 1
        val entity = PipelineEntity(name = name, topicKey = "test-topic")
        `when`(intakeService.findByName(name)).thenReturn(entity)

        mockMvc.perform(post("/pipeline/$name/run/$step"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pipelineName").value(name))
        
        verify(intakeService).runStep(name, step)
    }

    @Test
    fun `should run pipeline from step and return updated response`() {
        val name = "test-pipeline"
        val step = 0
        val entity = PipelineEntity(name = name, topicKey = "test-topic")
        `when`(intakeService.findByName(name)).thenReturn(entity)

        mockMvc.perform(post("/pipeline/$name/run-from/$step"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pipelineName").value(name))
        
        verify(intakeService).runPipelineFrom(name, step)
    }
}
