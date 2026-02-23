package com.kdob.piq.ai.client.quesiton

import com.kdob.piq.ai.client.quesiton.dto.QuestionPromptResponse
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
                    .queryParam("topicKeys", topicKeys)
                    .build()
            }
            .retrieve()
            .body<List<QuestionPromptResponse>>() ?: emptyList()
    }
}