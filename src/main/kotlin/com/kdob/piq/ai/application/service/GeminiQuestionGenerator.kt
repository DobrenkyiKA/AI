package com.kdob.piq.ai.application.service

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class GeminiQuestionGenerator(
    chatClientBuilder: ChatClient.Builder
) {
    val chatClient = chatClientBuilder.build()

    fun generateQuestions(prompt: String): String =
        chatClient.prompt().user(prompt).call().content() ?: "No response from AI"
}