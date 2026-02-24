package com.kdob.piq.ai.infrastructure.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class JwtAuthConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val roles = jwt.getClaimAsStringList("roles") ?: emptyList()

        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        return JwtAuthenticationToken(jwt, authorities)
    }
}