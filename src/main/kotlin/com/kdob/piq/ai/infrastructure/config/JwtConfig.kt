package com.kdob.piq.ai.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.security.interfaces.RSAPublicKey

@Configuration
class JwtConfig(
    @param:Value("\${rsa.public-key}")
    private val publicKey: RSAPublicKey
) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withPublicKey(publicKey).build()
    }
}