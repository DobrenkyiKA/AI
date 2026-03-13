package com.kdob.piq.ai.infrastructure.client.storage

interface StorageClient {

    fun saveTopicsArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String,
        content: String
    )

    fun saveQuestionsArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String,
        content: String
    )

    fun loadTopicsArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    ): String

    fun loadQuestionsArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    ): String

    fun deleteTopicsArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    )

    fun deleteQuestionsArtifact(
        topicKey: String,
        pipelineName: String,
        fileName: String
    )

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

    fun deleteVersion(
        topicKey: String,
        pipelineName: String
    )
}
