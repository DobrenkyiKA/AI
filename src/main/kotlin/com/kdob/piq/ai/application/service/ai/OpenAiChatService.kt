package com.kdob.piq.ai.application.service.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component


@Component
class OpenAiChatService(
    @param:Qualifier("openAiChatClient") private val chatClient: ChatClient
) {
    fun executePrompt(system: String, user: String): String =
        chatClient.prompt().system(system).user(user).call().content() ?: "ERRORINA"
}