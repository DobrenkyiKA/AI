package com.kdob.piq.ai.infrastructure.storage

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class ArtifactStorage(
    properties: PipelineArtifactProperties
) {
    private val rootDir: Path = Path.of(properties.rootDir)

    init {
        Files.createDirectories(rootDir)
    }

    fun saveStep0Artifact(pipelineName: String, yaml: String) {
        saveArtifact(pipelineName, 0, yaml)
    }

    fun loadStep0Artifact(pipelineName: String): String = loadArtifact(pipelineName, 0)

    fun saveStep1Questions(pipelineName: String, yaml: String) {
        saveArtifact(pipelineName, 1, yaml)
    }

    fun saveArtifact(pipelineName: String, step: Int, yaml: String) {
        val dir = rootDir.resolve("pipeline-$pipelineName")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(getArtifactFileName(step)), yaml)
    }

    fun loadArtifact(pipelineName: String, step: Int): String =
        Files.readString(rootDir.resolve("pipeline-$pipelineName/${getArtifactFileName(step)}"))

    private fun getArtifactFileName(step: Int): String = when (step) {
        0 -> "step-0-topic.yaml"
        1 -> "step-1-questions.generated.yaml"
        else -> throw IllegalArgumentException("Unknown step: $step")
    }

    fun deleteArtifacts(pipelineName: String) {
        val dir = rootDir.resolve("pipeline-$pipelineName")
        if (Files.exists(dir)) {
            dir.toFile().deleteRecursively()
        }
    }

    fun deleteArtifact(pipelineName: String, step: Int) {
        val dir = rootDir.resolve("pipeline-$pipelineName")
        val file = dir.resolve(getArtifactFileName(step))
        if (Files.exists(file)) {
            Files.delete(file)
        }
    }
}