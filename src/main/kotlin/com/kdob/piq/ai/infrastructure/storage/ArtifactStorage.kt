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

    fun saveTopicsArtifact(pipelineName: String, yaml: String) {
        saveArtifact(pipelineName, "TOPICS_GENERATION", yaml)
    }

    fun loadTopicsArtifact(pipelineName: String): String = loadArtifact(pipelineName, "TOPICS_GENERATION")

    fun saveQuestionsArtifact(pipelineName: String, yaml: String) {
        saveArtifact(pipelineName, "QUESTIONS_GENERATION", yaml)
    }

    fun saveArtifact(pipelineName: String, stepType: String, yaml: String) {
        val dir = rootDir.resolve(pipelineName)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(getArtifactFileName(stepType)), yaml)
    }

    fun loadArtifact(pipelineName: String, stepType: String): String =
        Files.readString(rootDir.resolve(pipelineName).resolve(getArtifactFileName(stepType)))

    private fun getArtifactFileName(stepType: String): String = when (stepType) {
        "TOPICS_GENERATION" -> "topics-artifact.yaml"
        "QUESTIONS_GENERATION" -> "questions-artifact.yaml"
        else -> throw IllegalArgumentException("Unknown step type: $stepType")
    }

    fun deleteArtifacts(pipelineName: String) {
        val dir = rootDir.resolve(pipelineName)
        if (Files.exists(dir)) {
            dir.toFile().deleteRecursively()
        }
    }

    fun deleteArtifact(pipelineName: String, stepType: String) {
        val dir = rootDir.resolve(pipelineName)
        val file = dir.resolve(getArtifactFileName(stepType))
        if (Files.exists(file)) {
            Files.delete(file)
        }
    }
}