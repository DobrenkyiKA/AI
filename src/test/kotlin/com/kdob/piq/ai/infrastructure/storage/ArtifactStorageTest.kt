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
    fun `should save topic tree artifact using topic key and pipeline name`() {
        storage.saveTopicTreeArtifact(topicKey, pipelineName, yaml)

        verify(storageClient).saveTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml", yaml)
    }

    @Test
    fun `should load topic tree artifact using topic key and pipeline name`() {
        `when`(storageClient.loadTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml")).thenReturn(yaml)

        val result = storage.loadTopicTreeArtifact(topicKey, pipelineName)

        assertEquals(yaml, result)
        verify(storageClient).loadTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml")
    }

    @Test
    fun `demonstrate consistency between save and load`() {
        `when`(storageClient.loadTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml")).thenReturn(yaml)

        storage.saveTopicTreeArtifact(topicKey, pipelineName, yaml)
        val result = storage.loadTopicTreeArtifact(topicKey, pipelineName)

        assertEquals(yaml, result)
    }
}
