package com.kdob.piq.ai.storage

import com.kdob.piq.ai.domain.GeneratedQuestion
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class Step1ArtifactStorage(
    private val rootDir: Path
) {

    fun save(
        pipelineId: UUID,
        questions: List<GeneratedQuestion>
    ): Path {

        val pipelineDir = rootDir.resolve("pipeline-$pipelineId")
        Files.createDirectories(pipelineDir)

        val artifactPath = pipelineDir.resolve("step-1-questions.generated.yaml")

        val yaml = Yaml()
        val content = mapOf("questions" to questions)
        Files.writeString(artifactPath, yaml.dump(content))

        return artifactPath
    }
}