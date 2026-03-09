package com.kdob.piq.ai.infrastructure.client.question

import com.kdob.piq.ai.infrastructure.client.question.dto.QuestionPromptResponse
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse

interface QuestionCatalogClient {

    fun findQuestionPrompts(
        topicKeys: Set<String>
    ): List<QuestionPromptResponse>

    fun findTopic(topicKey: String): TopicClientResponse?
}