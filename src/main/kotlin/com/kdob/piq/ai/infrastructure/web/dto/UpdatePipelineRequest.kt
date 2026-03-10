package com.kdob.piq.ai.infrastructure.web.dto

data class UpdatePipelineRequest(
    val topicKey: String? = null,
    val steps: List<UpdatePipelineStepRequest>? = null
)

data class UpdatePipelineStepRequest(
    val type: String,
    val systemPromptName: String? = null,
    val systemPrompt: String? = null,
    val userPromptName: String? = null,
    val userPrompt: String? = null
)
