package com.kdob.piq.ai.infrastructure.client.storage

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class StorageServiceClient(
    @param:Qualifier("storageRestClient") private val storageRestClient: RestClient
) {

    fun getVersions(): List<String> {
        val typeRef = object : ParameterizedTypeReference<List<String>>() {}
        return storageRestClient.get()
            .uri("/versions/prompts")
            .retrieve()
            .body(typeRef) ?: emptyList()
    }

    fun refresh() {
        storageRestClient.post()
            .uri("/versions/refresh")
            .retrieve()
            .toBodilessEntity()
    }

    fun deleteVersion(version: String) {
        storageRestClient.delete()
            .uri("/versions/prompts/$version")
            .retrieve()
            .toBodilessEntity()
    }

    fun getLastCommitMessage(version: String): String {
        return storageRestClient.get()
            .uri("/versions/prompts/$version/last-commit-message")
            .retrieve()
            .body(String::class.java) ?: ""
    }

    fun listPrompts(version: String): List<String> {
        val typeRef = object : ParameterizedTypeReference<List<String>>() {}
        return storageRestClient.get()
            .uri("/versions/prompts/$version")
            .retrieve()
            .body(typeRef) ?: emptyList()
    }

    fun getPromptFile(version: String, fileName: String): String? {
        return storageRestClient.get()
            .uri("/versions/prompts/$version/$fileName")
            .accept(MediaType.valueOf("text/plain;charset=UTF-8"))
            .retrieve()
            .body(String::class.java)
    }

    fun savePromptFile(version: String, fileName: String, content: String) {
        storageRestClient.put()
            .uri("/versions/prompts/$version/$fileName")
            .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
            .body(content)
            .retrieve()
            .toBodilessEntity()
    }

    fun commit(version: String, message: String) {
        storageRestClient.post()
            .uri("/versions/prompts/$version/commit")
            .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
            .body(message)
            .retrieve()
            .toBodilessEntity()
    }
}
