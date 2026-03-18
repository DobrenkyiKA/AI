package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.infrastructure.web.validation.PipelineName

data class CreatePipelineRequest(
    @field:PipelineName
    val name: String,
    val topicKey: String,
    val steps: List<CreatePipelineStepRequest> = emptyList()
)

data class CreatePipelineStepRequest(
    val type: String,
    val systemPromptName: String? = null,
    val systemPrompt: String? = null,
    val userPromptName: String? = null,
    val userPrompt: String? = null
)
