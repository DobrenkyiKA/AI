package com.kdob.piq.ai.infrastructure.storage

import com.kdob.piq.ai.infrastructure.client.storage.StorageClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ArtifactStorageTest {

    private val storageClient = mock(StorageClient::class.java)
    private val storage = ArtifactStorage(storageClient)
    private val topicKey = "test-topic"
    private val pipelineName = "test-pipeline"
    private val yaml = "content: test"

    @Test
    fun `should save topics artifact using topic key and pipeline name`() {
        storage.saveTopicsArtifact(topicKey, pipelineName, yaml)

        verify(storageClient).saveTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml", yaml)
    }

    @Test
    fun `should load topics artifact using topic key and pipeline name`() {
        `when`(storageClient.loadTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")).thenReturn(yaml)

        val result = storage.loadTopicsArtifact(topicKey, pipelineName)

        assertEquals(yaml, result)
        verify(storageClient).loadTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")
    }

    @Test
    fun `demonstrate consistency between save and load`() {
        `when`(storageClient.loadTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")).thenReturn(yaml)

        storage.saveTopicsArtifact(topicKey, pipelineName, yaml)
        val result = storage.loadTopicsArtifact(topicKey, pipelineName)

        assertEquals(yaml, result)
    }
}
