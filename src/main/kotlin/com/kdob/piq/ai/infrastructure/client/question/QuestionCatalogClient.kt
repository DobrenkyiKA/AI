package com.kdob.piq.ai.infrastructure.client.question

import com.kdob.piq.ai.infrastructure.client.question.dto.QuestionPromptResponse

interface QuestionCatalogClient {

    fun findQuestionPrompts(
        topicKeys: Set<String>
    ): List<QuestionPromptResponse>
}