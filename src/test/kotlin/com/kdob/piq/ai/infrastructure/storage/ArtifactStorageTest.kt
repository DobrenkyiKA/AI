package com.kdob.piq.ai.infrastructure.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArtifactStorageTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should save topics artifact using pipeline name`() {
        val properties = PipelineArtifactProperties(tempDir.toString())
        val storage = ArtifactStorage(properties)
        val pipelineName = "test-pipeline"
        val yaml = "content: test"

        storage.saveTopicsArtifact(pipelineName, yaml)

        val expectedPath = tempDir.resolve("$pipelineName/topics-artifact.yaml")
        assertTrue(Files.exists(expectedPath))
        assertEquals(yaml, Files.readString(expectedPath))
    }

    @Test
    fun `should load topics artifact using pipeline name`() {
        val properties = PipelineArtifactProperties(tempDir.toString())
        val storage = ArtifactStorage(properties)
        val pipelineName = "test-pipeline"
        val yaml = "content: loaded"

        val dir = tempDir.resolve(pipelineName)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("topics-artifact.yaml"), yaml)

        val result = storage.loadTopicsArtifact(pipelineName)

        assertEquals(yaml, result)
    }

    @Test
    fun `demonstrate consistency between save and load`() {
        val properties = PipelineArtifactProperties(tempDir.toString())
        val storage = ArtifactStorage(properties)
        val pipelineName = "test-pipeline"
        val yaml = "content: test"

        storage.saveTopicsArtifact(pipelineName, yaml)
        val result = storage.loadTopicsArtifact(pipelineName)
        assertEquals(yaml, result)
    }
}
