package com.kdob.piq.ai.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {

    @Bean
    fun questionServiceRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("http://localhost:8082")
            .build()
}