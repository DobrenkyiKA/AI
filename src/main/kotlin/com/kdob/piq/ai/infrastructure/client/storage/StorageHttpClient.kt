package com.kdob.piq.ai.infrastructure.client.storage

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class StorageHttpClient(
    @Qualifier("storageRestClient") private val restClient: RestClient
) : StorageClient {

    override fun saveTopicTreeArtifact(topicKey: String, pipelineName: String, fileName: String, content: String) {
        restClient.put()
            .uri("/versions/{group}/{version}/topic-tree/{fileName}", topicKey, pipelineName, fileName)
            .contentType(MediaType.TEXT_PLAIN)
            .body(content)
            .retrieve()
            .toBodilessEntity()
    }

    override fun loadTopicTreeArtifact(topicKey: String, pipelineName: String, fileName: String): String {
        return restClient.get()
            .uri("/versions/{group}/{version}/topic-tree/{fileName}", topicKey, pipelineName, fileName)
            .retrieve()
            .body(String::class.java) ?: ""
    }

    override fun deleteTopicTreeArtifact(topicKey: String, pipelineName: String, fileName: String) {
        restClient.delete()
            .uri("/versions/{group}/{version}/topic-tree/{fileName}", topicKey, pipelineName, fileName)
            .retrieve()
            .toBodilessEntity()
    }

    override fun saveAnswersArtifact(topicKey: String, pipelineName: String, fileName: String, content: String) {
        restClient.put()
            .uri("/versions/{group}/{version}/answers/{fileName}", topicKey, pipelineName, fileName)
            .contentType(MediaType.TEXT_PLAIN)
            .body(content)
            .retrieve()
            .toBodilessEntity()
    }

    override fun loadAnswersArtifact(topicKey: String, pipelineName: String, fileName: String): String {
        return restClient.get()
            .uri("/versions/{group}/{version}/answers/{fileName}", topicKey, pipelineName, fileName)
            .retrieve()
            .body(String::class.java) ?: ""
    }

    override fun deleteAnswersArtifact(topicKey: String, pipelineName: String, fileName: String) {
        restClient.delete()
            .uri("/versions/{group}/{version}/answers/{fileName}", topicKey, pipelineName, fileName)
            .retrieve()
            .toBodilessEntity()
    }

    override fun saveShortAnswersArtifact(topicKey: String, pipelineName: String, fileName: String, content: String) {
        restClient.put()
            .uri("/versions/{group}/{version}/short-answers/{fileName}", topicKey, pipelineName, fileName)
            .contentType(MediaType.TEXT_PLAIN)
            .body(content)
            .retrieve()
            .toBodilessEntity()
    }

    override fun loadShortAnswersArtifact(topicKey: String, pipelineName: String, fileName: String): String {
        return restClient.get()
            .uri("/versions/{group}/{version}/short-answers/{fileName}", topicKey, pipelineName, fileName)
            .retrieve()
            .body(String::class.java) ?: ""
    }

    override fun deleteShortAnswersArtifact(topicKey: String, pipelineName: String, fileName: String) {
        restClient.delete()
            .uri("/versions/{group}/{version}/short-answers/{fileName}", topicKey, pipelineName, fileName)
            .retrieve()
            .toBodilessEntity()
    }

    override fun deleteVersion(topicKey: String, pipelineName: String) {
        restClient.delete()
            .uri("/versions/{group}/{version}", topicKey, pipelineName)
            .retrieve()
            .toBodilessEntity()
    }
}
