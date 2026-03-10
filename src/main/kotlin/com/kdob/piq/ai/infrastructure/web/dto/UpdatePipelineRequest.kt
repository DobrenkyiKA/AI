package com.kdob.piq.ai.infrastructure.web.dto

data class UpdatePipelineRequest(
    val topicKey: String? = null,
    val steps: List<UpdatePipelineStepRequest>? = null
)

data class UpdatePipelineStepRequest(
    val type: String,
    val systemPrompt: String,
    val userPrompt: String
)
