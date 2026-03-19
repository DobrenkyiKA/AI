//package com.kdob.piq.ai.application.service.prompt
//
//import com.kdob.piq.ai.domain.model.PromptType
//import com.kdob.piq.ai.domain.repository.PromptRepository
//import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
//import com.kdob.piq.ai.infrastructure.web.dto.CreatePromptRequest
//import com.kdob.piq.ai.infrastructure.web.dto.UpdatePromptRequest
//import org.junit.jupiter.api.Assertions.*
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.mockito.Mockito.*
//
//class PromptServiceTest {
//
//    private lateinit var promptRepository: PromptRepository
//    private lateinit var promptSyncService: PromptSyncService
//    private lateinit var promptService: PromptService
//
//    @BeforeEach
//    fun setUp() {
//        promptRepository = mock(PromptRepository::class.java)
//        promptSyncService = mock(PromptSyncService::class.java)
//        promptService = PromptService(promptRepository, promptSyncService)
//    }
//
//    @Test
//    fun `should create prompt`() {
//        val request = CreatePromptRequest("test-prompt", PromptType.SYSTEM, "content")
//        `when`(promptRepository.findByName("test-prompt")).thenReturn(null)
//        `when`(promptRepository.save(any())).thenAnswer { it.getArgument(0) }
//
//        val response = promptService.create(request)
//
//        assertEquals("test-prompt", response.name)
//        assertEquals(PromptType.SYSTEM, response.type)
//        assertEquals("content", response.content)
//        verify(promptRepository).save(any())
//        verify(promptSyncService).exportToNewVersion(anyString())
//    }
//
//    @Test
//    fun `should fail to create prompt if name exists`() {
//        val request = CreatePromptRequest("test-prompt", PromptType.SYSTEM, "content")
//        `when`(promptRepository.findByName("test-prompt")).thenReturn(PromptEntity(PromptType.SYSTEM, "test-prompt", "existing"))
//
//        assertThrows(IllegalArgumentException::class.java) {
//            promptService.create(request)
//        }
//    }
//
//    @Test
//    fun `should update prompt content`() {
//        val existing = PromptEntity(PromptType.SYSTEM, "test-prompt", "old-content")
//        `when`(promptRepository.findByName("test-prompt")).thenReturn(existing)
//        `when`(promptRepository.save(any())).thenAnswer { it.getArgument(0) }
//
//        val response = promptService.update("test-prompt", UpdatePromptRequest(null, "new-content"))
//
//        assertEquals("new-content", response.content)
//        assertEquals("test-prompt", response.name)
//        verify(promptSyncService).exportToNewVersion(anyString())
//    }
//
//    @Test
//    fun `should update prompt name`() {
//        val existing = PromptEntity(PromptType.SYSTEM, "old-name", "content")
//        `when`(promptRepository.findByName("old-name")).thenReturn(existing)
//        `when`(promptRepository.findByName("new-name")).thenReturn(null)
//        `when`(promptRepository.save(any())).thenAnswer { it.getArgument(0) }
//
//        val response = promptService.update("old-name", UpdatePromptRequest("new-name", null))
//
//        assertEquals("new-name", response.name)
//        verify(promptRepository).findByName("new-name")
//        verify(promptSyncService).exportToNewVersion(anyString())
//    }
//
//    @Test
//    fun `should delete prompt`() {
//        promptService.delete("test-prompt")
//        verify(promptRepository).deleteByName("test-prompt")
//        verify(promptSyncService).exportToNewVersion(anyString())
//    }
//
//    private fun <T> any(): T = org.mockito.ArgumentMatchers.any()
//}
