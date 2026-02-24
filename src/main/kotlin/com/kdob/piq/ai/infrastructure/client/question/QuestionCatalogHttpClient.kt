package com.kdob.piq.ai.infrastructure.client.question

import com.kdob.piq.ai.infrastructure.client.question.dto.QuestionPromptResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class QuestionCatalogHttpClient(
    private val restClient: RestClient
) : QuestionCatalogClient {

    override fun findQuestionPrompts(
        topicKeys: Set<String>
    ): List<QuestionPromptResponse> {

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
}