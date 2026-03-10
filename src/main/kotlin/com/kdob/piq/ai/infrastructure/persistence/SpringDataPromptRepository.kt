package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.domain.model.PromptType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SpringDataPromptRepository : JpaRepository<PromptEntity, Long> {
    fun findByName(name: String): PromptEntity?
    fun deleteByName(name: String)
    fun findAllByType(type: PromptType): List<PromptEntity>
}
