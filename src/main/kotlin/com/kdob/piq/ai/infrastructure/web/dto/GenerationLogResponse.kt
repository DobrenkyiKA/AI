package com.kdob.piq.ai.infrastructure.web.dto

import java.time.Instant

data class GenerationLogResponse(
    val message: String,
    val stepOrder: Int?,
    val createdAt: Instant
)
