package com.kdob.piq.ai.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GatewayAuthFilter : OncePerRequestFilter() {

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
        const val USER_EMAIL_HEADER = "X-User-Email"
        const val USER_ROLES_HEADER = "X-User-Roles"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val userId = request.getHeader(USER_ID_HEADER)

        if (userId != null) {
            val email = request.getHeader(USER_EMAIL_HEADER) ?: ""
            val roles = request.getHeader(USER_ROLES_HEADER)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { SimpleGrantedAuthority("ROLE_$it") }
                ?: emptyList()

            val principal = GatewayPrincipal(userId, email)
            val authentication = UsernamePasswordAuthenticationToken(principal, null, roles)
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }
}

data class GatewayPrincipal(
    val userId: String,
    val email: String
)