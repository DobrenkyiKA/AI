package com.kdob.piq.ai.infrastructure.storage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.GeneratedQuestion
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class Step1ArtifactStorage(
    private val rootDir: Path
) {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun save(
        pipelineId: Long,
        questions: List<GeneratedQuestion>
    ): Path {

        val pipelineDir = rootDir.resolve("pipeline-$pipelineId")
        Files.createDirectories(pipelineDir)

        val artifactPath = pipelineDir.resolve("step-1-questions.generated.yaml")

        val content = mapOf("questions" to questions)
        Files.writeString(artifactPath, yamlMapper.writeValueAsString(content))

        return artifactPath
    }
}