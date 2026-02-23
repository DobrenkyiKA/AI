package com.kdob.piq.ai.client.quesiton

import com.kdob.piq.ai.client.quesiton.dto.QuestionPromptResponse

interface QuestionCatalogClient {

    fun findQuestionPrompts(
        topicKeys: Set<String>
    ): List<QuestionPromptResponse>
}