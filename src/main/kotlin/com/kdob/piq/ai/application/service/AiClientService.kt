package com.kdob.piq.ai.application.service

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class AiClientService(chatClientBuilder: ChatClient.Builder) {
    val chatClient = chatClientBuilder.build()

    fun testConnection(topic: String): String {
        return chatClient.prompt()
            .user("You are a Java Expert. Briefly explain the importance of $topic in a senior interview.")
            .call()
            .content() ?: "No response from AI"
    }
}