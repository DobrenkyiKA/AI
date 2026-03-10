package com.kdob.piq.ai.infrastructure.web.dto

data class CreatePipelineRequest(
    val name: String,
    val topicKey: String,
    val steps: List<CreatePipelineStepRequest> = emptyList()
)

data class CreatePipelineStepRequest(
    val type: String,
    val systemPrompt: String = "",
    val userPrompt: String = ""
)
