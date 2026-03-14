package com.kdob.piq.ai.infrastructure.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatClientConfiguration {
    @Bean("openAiChatClient")
    fun openAiChatClient(model: OpenAiChatModel): ChatClient =
        ChatClient.builder(model)
            .defaultSystem("You are a helpful assistant powered by OpenAI.")
            .build()

//    @Bean("googleChatClient")
//    fun googleChatClient(model: GoogleAiChatModel): ChatClient =
//        ChatClient.builder(model)
//            .defaultSystem("You are a helpful assistant powered by Google Gemini.")
//            .build()
}