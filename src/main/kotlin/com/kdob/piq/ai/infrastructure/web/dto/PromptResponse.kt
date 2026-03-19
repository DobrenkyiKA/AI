package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.PromptType

data class PromptResponse(
    val type: PromptType,
    val name: String,
    val content: String
)
