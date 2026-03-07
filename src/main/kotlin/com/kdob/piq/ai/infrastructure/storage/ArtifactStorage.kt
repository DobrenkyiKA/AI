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
        val dir = rootDir.resolve("pipeline-$pipelineName")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("step-0-topic.yaml"), yaml)
    }

    fun loadStep0Artifact(pipelineName: String): String =
        Files.readString(rootDir.resolve("pipeline-$pipelineName/step-0-topic.yaml"))

    fun saveStep1Questions(pipelineName: String, yaml: String) {
        Files.writeString(
            rootDir.resolve("pipeline-$pipelineName/step-1-questions.generated.yaml"),
            yaml
        )
    }

    fun loadStep1Questions(pipelineName: String): String =
        Files.readString(
            rootDir.resolve("pipeline-$pipelineName/step-1-questions.generated.yaml")
        )
}