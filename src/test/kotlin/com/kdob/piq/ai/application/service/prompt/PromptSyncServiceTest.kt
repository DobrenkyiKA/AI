package com.kdob.piq.ai.application.service.prompt

import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.client.storage.StorageServiceClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class PromptSyncServiceTest {

    private lateinit var promptRepository: PromptRepository
    private lateinit var storageServiceClient: StorageServiceClient
    private lateinit var promptSyncService: PromptSyncService

    @BeforeEach
    fun setUp() {
        promptRepository = mock(PromptRepository::class.java)
        storageServiceClient = mock(StorageServiceClient::class.java)
        promptSyncService = PromptSyncService(promptRepository, storageServiceClient)
    }

    @Test
    fun `exportToVersion should save prompts to storage and commit`() {
        val version = "v1"
        val message = "Commit message"
        val prompts = listOf(
            PromptEntity(PromptType.SYSTEM, "name1", "content1"),
            PromptEntity(PromptType.USER, "name2", "content2")
        )
        `when`(promptRepository.findAll()).thenReturn(prompts)

        promptSyncService.exportToVersion(version, message)

        val contentCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(storageServiceClient).savePromptFile(eq(version), eq("default-prompts.yaml"), capture(contentCaptor))
        verify(storageServiceClient).commit(version, message)

        val content = contentCaptor.value
        assertTrue(content.contains("name1"))
        assertTrue(content.contains("content1"))
        assertTrue(content.contains("name2"))
        assertTrue(content.contains("content2"))
    }

    @Test
    fun `importFromVersion should update existing and create new prompts`() {
        val version = "v1"
        val yamlContent = """
            prompts:
              - name: "existing"
                type: "SYSTEM"
                content: "new content"
              - name: "new"
                type: "USER"
                content: "brand new"
        """.trimIndent()
        
        `when`(storageServiceClient.getPromptFile(version, "default-prompts.yaml")).thenReturn(yamlContent)
        
        val existingPrompt = PromptEntity(PromptType.SYSTEM, "existing", "old content")
        `when`(promptRepository.findByName("existing")).thenReturn(existingPrompt)
        `when`(promptRepository.findByName("new")).thenReturn(null)
        `when`(promptRepository.findAll()).thenReturn(listOf(existingPrompt))

        promptSyncService.importFromVersion(version)

        verify(promptRepository).save(existingPrompt)
        assertEquals("new content", existingPrompt.content)
        
        verify(promptRepository, times(2)).save(any(PromptEntity::class.java))
    }

    @Test
    fun `exportToNewVersion should create next Auto version`() {
        `when`(storageServiceClient.getVersions()).thenReturn(listOf("v1", "Auto v1", "Auto v2"))
        `when`(promptRepository.findAll()).thenReturn(emptyList())

        promptSyncService.exportToNewVersion("Auto test")

        verify(storageServiceClient).savePromptFile(eq("Auto v3"), eq("default-prompts.yaml"), anyString())
        verify(storageServiceClient).commit(eq("Auto v3"), eq("Auto test"))
    }

    @Test
    fun `exportToNewVersion should create Auto v1 if no versions`() {
        `when`(storageServiceClient.getVersions()).thenReturn(emptyList())
        `when`(promptRepository.findAll()).thenReturn(emptyList())

        promptSyncService.exportToNewVersion("Auto test")

        verify(storageServiceClient).savePromptFile(eq("Auto v1"), eq("default-prompts.yaml"), anyString())
    }

    private fun <T> capture(captor: ArgumentCaptor<T>): T {
        captor.capture()
        @Suppress("UNCHECKED_CAST")
        return "" as T
    }

    private fun <T> eq(value: T): T {
        org.mockito.ArgumentMatchers.eq(value)
        return value
    }

    private fun <T> any(type: Class<T>): T {
        org.mockito.ArgumentMatchers.any(type)
        return if (type == PromptEntity::class.java) {
            PromptEntity(type = PromptType.SYSTEM, name = "dummy", content = "dummy") as T
        } else {
            @Suppress("UNCHECKED_CAST")
            null as T
        }
    }

    @Test
    fun `importFromVersion should delete prompts not in imported list`() {
        val version = "v1"
        val yamlContent = """
            prompts:
              - name: "kept"
                type: "SYSTEM"
                content: "content"
        """.trimIndent()
        
        `when`(storageServiceClient.getPromptFile(version, "default-prompts.yaml")).thenReturn(yamlContent)
        
        val keptPrompt = PromptEntity(PromptType.SYSTEM, "kept", "content")
        val removedPrompt = PromptEntity(PromptType.USER, "removed", "content")
        
        `when`(promptRepository.findByName("kept")).thenReturn(keptPrompt)
        `when`(promptRepository.findAll()).thenReturn(listOf(keptPrompt, removedPrompt))

        promptSyncService.importFromVersion(version)

        verify(promptRepository).deleteByName("removed")
        verify(promptRepository, never()).deleteByName("kept")
    }
}
