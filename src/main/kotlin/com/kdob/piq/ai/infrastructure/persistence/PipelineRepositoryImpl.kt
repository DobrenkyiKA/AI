package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class PipelineRepositoryImpl : PipelineRepository {
    override fun save(pipeline: PipelineDefinitionForm) = TODO()
    override fun findById(id: Long): PipelineEntity? = TODO()
    override fun updateStatus(id: Long, status: PipelineStatus) = TODO()
}