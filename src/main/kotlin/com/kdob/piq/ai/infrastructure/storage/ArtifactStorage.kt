package com.kdob.piq.ai.infrastructure.storage

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@Component
class ArtifactStorage(
    properties: PipelineArtifactProperties
) {
    private val rootDir: Path = Path.of(properties.rootDir)

    init {
        Files.createDirectories(rootDir)
    }

    fun saveStep0Artifact(pipelineId: UUID, yaml: String) {
        val dir = rootDir.resolve("pipeline-$pipelineId")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("step-0-topic.yaml"), yaml)
    }

    fun loadStep0Artifact(pipelineId: UUID): String =
        Files.readString(rootDir.resolve("pipeline-$pipelineId/step-0-topic.yaml"))

    fun saveStep1Questions(pipelineId: UUID, yaml: String) {
        Files.writeString(
            rootDir.resolve("pipeline-$pipelineId/step-1-questions.generated.yaml"),
            yaml
        )
    }

    fun loadStep1Questions(pipelineId: UUID): String =
        Files.readString(
            rootDir.resolve("pipeline-$pipelineId/step-1-questions.generated.yaml")
        )
}