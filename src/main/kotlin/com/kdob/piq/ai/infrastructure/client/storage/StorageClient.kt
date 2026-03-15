package com.kdob.piq.ai.infrastructure.client.storage

interface StorageClient {

    fun saveTopicTreeArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String,
        content: String
    )

    fun loadTopicTreeArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    ): String

    fun deleteTopicTreeArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    )

    fun saveAnswersArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String,
        content: String
    )

    fun loadAnswersArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    ): String

    fun deleteAnswersArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    )

    fun saveShortAnswersArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String,
        content: String
    )

    fun loadShortAnswersArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    ): String

    fun deleteShortAnswersArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    )

    fun deleteVersion(
        topicKey: String,
        pipelineName: String
    )
}
