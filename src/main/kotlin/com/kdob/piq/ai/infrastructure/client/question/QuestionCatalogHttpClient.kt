package com.kdob.piq.ai.infrastructure.client.question

import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.client.question.dto.QuestionPromptResponse
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class QuestionCatalogHttpClient(private val restClient: RestClient) : QuestionCatalogClient {

    override fun findQuestionPrompts(topicKeys: Set<String>): List<QuestionPromptResponse> {

        if (topicKeys.isEmpty()) {
            return emptyList()
        }

        return restClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/question-prompts")
                    .queryParam("topicKeys", *topicKeys.toTypedArray())
                    .build()
            }
            .retrieve()
            .body<List<QuestionPromptResponse>>() ?: emptyList()
    }

    override fun findTopic(topicKey: String): TopicClientResponse? {
        return restClient.get()
            .uri("/topics/{key}", topicKey)
            .retrieve()
            .body<TopicClientResponse>()
    }

    override fun createTopic(request: CreateTopicClientRequest): TopicClientResponse {
        return restClient.post()
            .uri("/admin/topics")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<TopicClientResponse>() ?: throw IllegalStateException("Failed to create topic: ${request.key}")
    }
}