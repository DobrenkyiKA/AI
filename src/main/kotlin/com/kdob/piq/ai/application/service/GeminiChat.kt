package com.kdob.piq.ai.application.service

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class GeminiChat(chatClientBuilder: ChatClient.Builder) {
    val chatClient = chatClientBuilder.build()

    fun executePrompt(prompt: String): String =
        chatClient.prompt().user(prompt).call().content() ?: "No response from AI"
}