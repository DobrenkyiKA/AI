package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.infrastructure.web.validation.PromptName
import jakarta.validation.constraints.NotNull

data class CreatePromptRequest(
    @field:PromptName
    val name: String,

    @field:NotNull
    val type: PromptType,

    @field:NotNull
    val content: String
)

data class UpdatePromptRequest(
    @field:PromptName
    val name: String,

    @field:NotNull
    val content: String
)
