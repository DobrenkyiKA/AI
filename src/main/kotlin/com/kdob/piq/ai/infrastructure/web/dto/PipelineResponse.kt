package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.ArtifactStatus
import java.time.Instant

data class PipelineResponse(
    val pipelineName: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val steps: List<PipelineStepResponse>
)

data class PipelineStepResponse(
    val step: Int,
    val status: ArtifactStatus?
)
