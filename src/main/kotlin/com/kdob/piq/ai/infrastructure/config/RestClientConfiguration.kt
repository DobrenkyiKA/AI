package com.kdob.piq.ai.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @param:Value("\${question.service.url:http://localhost:8082}") private val questionServiceUrl: String,
    @param:Value("\${storage.url:http://localhost:8084}") private val storageUrl: String
) {

    @Bean
    fun questionServiceRestClient(): RestClient =
        createRestClient(questionServiceUrl)

    @Bean
    fun storageRestClient(): RestClient =
        createRestClient(storageUrl)

    private fun createRestClient(baseUrl: String): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestInterceptor { request, body, execution ->
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication is JwtAuthenticationToken) {
                    val tokenValue = authentication.token.tokenValue
                    request.headers.add("Authorization", "Bearer $tokenValue")
                }
                execution.execute(request, body)
            }
            .build()
}