package com.kdob.piq.ai.infrastructure.client.question.dto

data class QuestionPromptResponse(
    val prompt: String,
    val topicKey: String
)