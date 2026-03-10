package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.domain.model.PromptType

interface PromptRepository {
    fun findByName(name: String): PromptEntity?
    fun save(prompt: PromptEntity): PromptEntity
    fun deleteByName(name: String)
    fun findAllByType(type: PromptType): List<PromptEntity>
}
