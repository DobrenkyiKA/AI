package com.kdob.piq.ai.infrastructure.web.mapper

import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.infrastructure.web.dto.PromptResponse

object PromptMapper {
    fun PromptEntity.toResponse() = PromptResponse(
        type = this.type,
        name = this.name,
        content = this.content
    )
}