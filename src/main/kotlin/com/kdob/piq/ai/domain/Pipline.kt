package com.kdob.piq.ai.domain

import java.time.Instant
import java.util.UUID

data class Pipeline(
    val id: UUID,
    val name: String,
    val status: PipelineStatus,
    val createdAt: Instant
)