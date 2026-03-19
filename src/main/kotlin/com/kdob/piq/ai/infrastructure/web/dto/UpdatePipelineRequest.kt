package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.infrastructure.web.validation.PipelineName

data class UpdatePipelineRequest(
    @field:PipelineName
    val name: String,
    val steps: List<UpdatePipelineStepRequest>? = null
)

data class UpdatePipelineStepRequest(
    val type: String,
    val systemPromptName: String? = null,
    val systemPrompt: String? = null,
    val userPromptName: String? = null,
    val userPrompt: String? = null
)
