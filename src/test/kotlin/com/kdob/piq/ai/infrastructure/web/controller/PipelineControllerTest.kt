package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.step0.Step0TopicIntakeService
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
    private val intakeService: Step0TopicIntakeService = mock(Step0TopicIntakeService::class.java)
    private val controller = PipelineController(intakeService)

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `should create pipeline and return 201 with Location header`() {
        val yaml = "pipeline: { name: 'test-pipeline' }"
        val entity = PipelineEntity(name = "test-pipeline")
        
        `when`(intakeService.intake(yaml)).thenReturn(entity)

        mockMvc.perform(post("/pipeline")
            .contentType(MediaType.TEXT_PLAIN)
            .content(yaml))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "http://localhost/pipeline/test-pipeline"))
            .andExpect(jsonPath("$.pipelineName").value("test-pipeline"))
    }

    @Test
    fun `should return 204 when deleting pipeline`() {
        val name = "test-pipeline"

        mockMvc.perform(delete("/pipeline/$name"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `should return 200 when getting all pipelines`() {
        val entity = PipelineEntity(name = "test-pipeline")
        `when`(intakeService.findAll()).thenReturn(listOf(entity))

        mockMvc.perform(get("/pipeline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].pipelineName").value("test-pipeline"))
    }

    @Test
    fun `should return 200 when getting single pipeline`() {
        val name = "test-pipeline"
        val entity = PipelineEntity(name = name)
        `when`(intakeService.findByName(name)).thenReturn(entity)

        mockMvc.perform(get("/pipeline/$name"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pipelineName").value(name))
    }
}
