package com.kdob.piq.ai.infrastructure.web.dto

import java.time.Instant

data class PipelineResponse(
    val pipelineName: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
