package com.kdob.piq.ai.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @param:Value("\${question.service.url:http://localhost:8082}") private val questionServiceUrl: String,
    @param:Value("\${storage.url:http://localhost:8084}") private val storageUrl: String
) {
    @Bean
    fun questionServiceRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(questionServiceUrl)
            .build()

    @Bean
    fun storageRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(storageUrl)
            .build()
}