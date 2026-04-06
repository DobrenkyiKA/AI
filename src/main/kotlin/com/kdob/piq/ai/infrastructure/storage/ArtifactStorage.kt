package com.kdob.piq.ai.infrastructure.storage

import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.infrastructure.client.storage.StorageClient
import org.springframework.stereotype.Component

@Component
class ArtifactStorage(
    private val storageClient: StorageClient
) {
    fun saveArtifact(topicKey: String, pipelineName: String, stepType: StepType, yaml: String) {
        when (stepType) {
            StepType.TOPIC_TREE_GENERATION -> storageClient.saveTopicTreeArtifact(topicKey, pipelineName, stepType.artifactFileName, yaml)
            StepType.QUESTIONS_GENERATION -> storageClient.saveQuestionsArtifact(topicKey, pipelineName, stepType.artifactFileName, yaml)
            StepType.LONG_ANSWERS_GENERATION -> storageClient.saveAnswersArtifact(topicKey, pipelineName, stepType.artifactFileName, yaml)
            StepType.SHORT_ANSWERS_GENERATION -> storageClient.saveShortAnswersArtifact(topicKey, pipelineName, stepType.artifactFileName, yaml)
        }
    }

    fun loadArtifact(topicKey: String, pipelineName: String, stepType: StepType): String = when (stepType) {
        StepType.TOPIC_TREE_GENERATION -> storageClient.loadTopicTreeArtifact(topicKey, pipelineName, stepType.artifactFileName)
        StepType.QUESTIONS_GENERATION -> storageClient.loadQuestionsArtifact(topicKey, pipelineName, stepType.artifactFileName)
        StepType.LONG_ANSWERS_GENERATION -> storageClient.loadAnswersArtifact(topicKey, pipelineName, stepType.artifactFileName)
        StepType.SHORT_ANSWERS_GENERATION -> storageClient.loadShortAnswersArtifact(topicKey, pipelineName, stepType.artifactFileName)
    }

    fun deleteArtifacts(topicKey: String, pipelineName: String) {
        storageClient.deleteVersion(topicKey, pipelineName)
    }

    fun deleteArtifact(topicKey: String, pipelineName: String, stepType: StepType) {
        when (stepType) {
            StepType.TOPIC_TREE_GENERATION -> storageClient.deleteTopicTreeArtifact(topicKey, pipelineName, stepType.artifactFileName)
            StepType.QUESTIONS_GENERATION -> storageClient.deleteQuestionsArtifact(topicKey, pipelineName, stepType.artifactFileName)
            StepType.LONG_ANSWERS_GENERATION -> storageClient.deleteAnswersArtifact(topicKey, pipelineName, stepType.artifactFileName)
            StepType.SHORT_ANSWERS_GENERATION -> storageClient.deleteShortAnswersArtifact(topicKey, pipelineName, stepType.artifactFileName)
        }
    }
}
