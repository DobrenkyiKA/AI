package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.infrastructure.persistence.entity.GenerationLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GenerationLogRepository : JpaRepository<GenerationLogEntity, Long> {
    fun findByPipelineNameOrderByCreatedAtAsc(pipelineName: String): List<GenerationLogEntity>
}
