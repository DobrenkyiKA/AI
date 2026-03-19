//package com.kdob.piq.ai.infrastructure.web.controller
//
//import com.kdob.piq.ai.application.service.prompt.PromptService
//import com.kdob.piq.ai.domain.model.PromptType
//import com.kdob.piq.ai.infrastructure.web.controller.PromptController
//import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
//import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
//import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.mockito.Mockito.*
//import org.springframework.http.MediaType
//import org.springframework.test.web.servlet.MockMvc
//import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
//import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
//import org.springframework.test.web.servlet.setup.MockMvcBuilders
//
//class PromptControllerTest {
//
//    private lateinit var mockMvc: MockMvc
//    private val promptService: PromptService = mock(PromptService::class.java)
//    private val controller = PromptController(promptService)
//
//    @BeforeEach
//    fun setup() {
//        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
//    }
//
//    @Test
//    fun `should return all prompts`() {
//        val response = PromptResponse(1L, PromptType.SYSTEM, "test", "content")
//        `when`(promptService.findAll()).thenReturn(listOf(response))
//
//        mockMvc.perform(get("/prompts"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$[0].name").value("test"))
//    }
//
//    @Test
//    fun `should return prompts by type`() {
//        val response = PromptResponse(1L, PromptType.SYSTEM, "test", "content")
//        `when`(promptService.findAllByType(PromptType.SYSTEM)).thenReturn(listOf(response))
//
//        mockMvc.perform(get("/prompts").param("type", "SYSTEM"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$[0].name").value("test"))
//    }
//
//    @Test
//    fun `should return prompt by name`() {
//        val name = "test"
//        val response = PromptResponse(1L, PromptType.SYSTEM, name, "content")
//        `when`(promptService.get(name)).thenReturn(response)
//
//        mockMvc.perform(get("/prompts/$name"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.name").value(name))
//    }
//
//    @Test
//    fun `should create prompt`() {
//        val request = CreatePromptRequest("test", PromptType.SYSTEM, "content")
//        val response = PromptResponse(1L, PromptType.SYSTEM, "test", "content")
//        `when`(promptService.create(any(CreatePromptRequest::class.java))).thenReturn(response)
//
//        mockMvc.perform(post("/prompts")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"name\": \"test\", \"type\": \"SYSTEM\", \"content\": \"content\"}"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.name").value("test"))
//    }
//
//    @Test
//    fun `should update prompt`() {
//        val name = "test"
//        val request = UpdatePromptRequest("new-name", "new-content")
//        val response = PromptResponse(1L, PromptType.SYSTEM, "new-name", "new-content")
//        `when`(promptService.update(eq(name) ?: name, any(UpdatePromptRequest::class.java))).thenReturn(response)
//
//        mockMvc.perform(put("/prompts/$name")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"name\": \"new-name\", \"content\": \"new-content\"}"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.name").value("new-name"))
//    }
//
//    @Test
//    fun `should delete prompt`() {
//        val name = "test"
//
//        mockMvc.perform(delete("/prompts/$name"))
//            .andExpect(status().isOk)
//
//        verify(promptService).delete(name)
//    }
//
//    private fun <T> any(type: Class<T>): T = org.mockito.ArgumentMatchers.any(type)
//}
