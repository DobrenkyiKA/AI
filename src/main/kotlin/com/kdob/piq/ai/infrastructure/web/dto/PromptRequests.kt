package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.PromptType

data class CreatePromptRequest(
    val name: String,
    val type: PromptType,
    val content: String
)

data class UpdatePromptRequest(
    val name: String?,
    val content: String?
)
