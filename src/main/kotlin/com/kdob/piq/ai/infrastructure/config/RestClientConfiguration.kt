package com.kdob.piq.ai.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {

    @Bean
    fun questionServiceRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("http://localhost:8082")
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