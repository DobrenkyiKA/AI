package com.kdob.piq.ai.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val gatewayAuthFilter: GatewayAuthFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }

            addFilterBefore<UsernamePasswordAuthenticationFilter>(gatewayAuthFilter)

            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/health", permitAll)
                authorize("/admin/**", hasRole("ADMIN"))
                authorize("/pipeline/**", hasRole("ADMIN"))
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }
}