package com.kdob.piq.ai.application.service

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component


@Component
class OpenAiChatService(
    @Qualifier("openAiChatClient") private val chatClient: ChatClient
) {

    fun ask(userMessage: String): String = chatClient.prompt().user(userMessage).call().content() ?: ""

    fun executePrompt(system: String, user: String): String =
        chatClient.prompt().system(system).user(user).call().content() ?: "ERRORINA"
}