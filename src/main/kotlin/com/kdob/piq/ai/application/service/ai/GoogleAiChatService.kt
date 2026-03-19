package com.kdob.piq.ai.application.service.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class GoogleAiChatService(chatClientBuilder: ChatClient.Builder) {
    val chatClient = chatClientBuilder.build()

    fun executePrompt(system: String, user: String): String =
        chatClient.prompt()
            .system(system)
            .user(user)
            .call()
            .content() ?: "No response from AI"
}