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

    fun saveTopicTreeArtifact(topicKey: String, pipelineName: String, yaml: String) {
        storageClient.saveTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml", yaml)
    }

    fun loadTopicTreeArtifact(topicKey: String, pipelineName: String): String =
        storageClient.loadTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml")

    fun saveAnswersArtifact(topicKey: String, pipelineName: String, yaml: String) {
        storageClient.saveAnswersArtifact(topicKey, pipelineName, "answers-artifact.yaml", yaml)
    }

    fun loadAnswersArtifact(topicKey: String, pipelineName: String): String =
        storageClient.loadAnswersArtifact(topicKey, pipelineName, "answers-artifact.yaml")

    fun saveShortAnswersArtifact(topicKey: String, pipelineName: String, yaml: String) {
        storageClient.saveShortAnswersArtifact(topicKey, pipelineName, "short-answers-artifact.yaml", yaml)
    }

    fun loadShortAnswersArtifact(topicKey: String, pipelineName: String): String =
        storageClient.loadShortAnswersArtifact(topicKey, pipelineName, "short-answers-artifact.yaml")

    fun loadArtifact(topicKey: String, pipelineName: String, stepType: String): String = when (stepType) {
        "TOPICS_GENERATION", "SUBTOPICS_GENERATION" -> storageClient.loadTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")
        "QUESTIONS_GENERATION" -> storageClient.loadQuestionsArtifact(topicKey, pipelineName, "questions-artifact.yaml")
        "TOPIC_TREE_GENERATION" -> storageClient.loadTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml")
        "LONG_ANSWERS_GENERATION" -> storageClient.loadAnswersArtifact(topicKey, pipelineName, "answers-artifact.yaml")
        "SHORT_ANSWERS_GENERATION" -> storageClient.loadShortAnswersArtifact(topicKey, pipelineName, "short-answers-artifact.yaml")
        else -> throw IllegalArgumentException("Unknown step type: $stepType")
    }

    fun deleteArtifacts(topicKey: String, pipelineName: String) {
        storageClient.deleteVersion(topicKey, pipelineName)
    }

    fun deleteArtifact(topicKey: String, pipelineName: String, stepType: String) {
        when (stepType) {
            "TOPICS_GENERATION", "SUBTOPICS_GENERATION" -> storageClient.deleteTopicsArtifact(topicKey, pipelineName, "topics-artifact.yaml")
            "QUESTIONS_GENERATION" -> storageClient.deleteQuestionsArtifact(topicKey, pipelineName, "questions-artifact.yaml")
            "TOPIC_TREE_GENERATION" -> storageClient.deleteTopicTreeArtifact(topicKey, pipelineName, "topic-tree-artifact.yaml")
            "LONG_ANSWERS_GENERATION" -> storageClient.deleteAnswersArtifact(topicKey, pipelineName, "answers-artifact.yaml")
            "SHORT_ANSWERS_GENERATION" -> storageClient.deleteShortAnswersArtifact(topicKey, pipelineName, "short-answers-artifact.yaml")
            else -> throw IllegalArgumentException("Unknown step type: $stepType")
        }
    }
}
