package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.ArtifactStatus
import java.time.Instant

data class PipelineResponse(
    val pipelineName: String,
    val topicKey: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val steps: List<PipelineStepResponse>,
    val logs: List<GenerationLogResponse> = emptyList()
)

data class GenerationLogResponse(
    val message: String,
    val createdAt: Instant
)

data class PipelineStepResponse(
    val step: Int,
    val type: String,
    val status: ArtifactStatus?,
    val systemPromptName: String?,
    val systemPrompt: String,
    val userPromptName: String?,
    val userPrompt: String
)
