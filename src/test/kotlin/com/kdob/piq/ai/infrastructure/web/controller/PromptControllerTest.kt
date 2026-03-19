//package com.kdob.piq.ai.infrastructure.web.controller
//
//import com.kdob.piq.ai.domain.model.PromptType
//import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
//import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse
//import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
//import com.kdob.piq.ai.infrastructure.web.facade.PromptFacade
//import org.hamcrest.Matchers.endsWith
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
//    private val promptFacade: PromptFacade = mock(PromptFacade::class.java)
//    private val controller = PromptController(promptFacade)
//
//    @BeforeEach
//    fun setup() {
//        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
//    }
//
//    @Test
//    fun `should return all prompts`() {
//        val response = PromptResponse(PromptType.SYSTEM, "TEST_PROMPT", "content")
//        `when`(promptFacade.getAll()).thenReturn(listOf(response))
//
//        mockMvc.perform(get("/prompts"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$[0].name").value("TEST_PROMPT"))
//    }
//
//    @Test
//    fun `should return prompts by type`() {
//        val response = PromptResponse(PromptType.SYSTEM, "TEST_PROMPT", "content")
//        `when`(promptFacade.getByType(PromptType.SYSTEM)).thenReturn(listOf(response))
//
//        mockMvc.perform(get("/prompts").param("type", "SYSTEM"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$[0].name").value("TEST_PROMPT"))
//    }
//
//    @Test
//    fun `should return prompt by name`() {
//        val name = "TEST_PROMPT"
//        val response = PromptResponse(PromptType.SYSTEM, name, "content")
//        `when`(promptFacade.get(name)).thenReturn(response)
//
//        mockMvc.perform(get("/prompts/$name"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.name").value(name))
//    }
//
//    @Test
//    fun `should create prompt`() {
//        val response = PromptResponse(PromptType.SYSTEM, "TEST_PROMPT", "content")
//        `when`(promptFacade.create(any(CreatePromptRequest::class.java))).thenReturn(response)
//
//        mockMvc.perform(post("/prompts")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"name\": \"TEST_PROMPT\", \"type\": \"SYSTEM\", \"content\": \"content\"}"))
//            .andExpect(status().isCreated)
//            .andExpect(header().string("Location", endsWith("/prompts/TEST_PROMPT")))
//            .andExpect(jsonPath("$.name").value("TEST_PROMPT"))
//    }
//
//    @Test
//    fun `should update prompt`() {
//        val name = "TEST_PROMPT"
//        val response = PromptResponse(PromptType.SYSTEM, "NEW_NAME", "new-content")
//        `when`(promptFacade.update(eq(name), any(UpdatePromptRequest::class.java))).thenReturn(response)
//
//        mockMvc.perform(put("/prompts/$name")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"name\": \"NEW_NAME\", \"content\": \"new-content\"}"))
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.name").value("NEW_NAME"))
//    }
//
//    @Test
//    fun `should delete prompt`() {
//        val name = "TEST_PROMPT"
//
//        mockMvc.perform(delete("/prompts/$name"))
//            .andExpect(status().isNoContent)
//
//        verify(promptFacade).delete(name)
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    private fun <T> any(type: Class<T>): T {
//        org.mockito.ArgumentMatchers.any(type)
//        return if (type == CreatePromptRequest::class.java) {
//            CreatePromptRequest("dummy", PromptType.SYSTEM, "dummy") as T
//        } else if (type == UpdatePromptRequest::class.java) {
//            UpdatePromptRequest("dummy", "dummy") as T
//        } else {
//            null as T
//        }
//    }
//
//    private fun <T> eq(value: T): T {
//        org.mockito.ArgumentMatchers.eq(value)
//        return value
//    }
//}
