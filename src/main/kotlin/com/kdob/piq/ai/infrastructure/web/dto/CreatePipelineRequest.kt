package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import com.kdob.piq.ai.infrastructure.web.validation.TopicKey

data class CreatePipelineRequest(
    @field:PipelineName
    val name: String,
    @field:TopicKey
    val topicKey: String,
    val steps: List<CreatePipelineStepRequest> = emptyList()
)

data class CreatePipelineStepRequest(
    val type: String,
    val systemPromptName: String,
    val systemPrompt: String? = null,
    val userPromptName: String,
    val userPrompt: String? = null
)
