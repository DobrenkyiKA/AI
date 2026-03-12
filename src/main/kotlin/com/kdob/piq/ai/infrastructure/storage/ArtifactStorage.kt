package com.kdob.piq.ai.infrastructure.storage

import com.kdob.piq.ai.infrastructure.client.storage.StorageClient
import org.springframework.stereotype.Component

@Component
class ArtifactStorage(
    private val storageClient: StorageClient
) {
    fun saveTopicsArtifact(topicKey: String, pipelineName: String, yaml: String) {
        storageClient.saveTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml", yaml)
    }

    fun loadTopicsArtifact(topicKey: String, pipelineName: String): String =
        storageClient.loadTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")

    fun saveQuestionsArtifact(topicKey: String, pipelineName: String, yaml: String) {
        storageClient.saveQuestionsArtifact(topicKey, pipelineName, "questions-artifact.yaml", yaml)
    }

    fun loadArtifact(topicKey: String, pipelineName: String, stepType: String): String = when (stepType) {
        "TOPICS_GENERATION", "SUBTOPICS_GENERATION" -> storageClient.loadTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")
        "QUESTIONS_GENERATION" -> storageClient.loadQuestionsArtifact(topicKey, pipelineName, "questions-artifact.yaml")
        else -> throw IllegalArgumentException("Unknown step type: $stepType")
    }

    fun deleteArtifacts(topicKey: String, pipelineName: String) {
        storageClient.deleteVersion(topicKey, pipelineName)
    }

    fun deleteArtifact(topicKey: String, pipelineName: String, stepType: String) {
        when (stepType) {
            "TOPICS_GENERATION", "SUBTOPICS_GENERATION" -> storageClient.deleteTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")
            "QUESTIONS_GENERATION" -> storageClient.deleteQuestionsArtifact(topicKey, pipelineName, "questions-artifact.yaml")
            else -> throw IllegalArgumentException("Unknown step type: $stepType")
        }
    }
}