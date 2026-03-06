package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import java.util.*


interface PipelineRepository {

    fun save(pipeline: PipelineDefinitionForm): PipelineDefinitionForm
    fun findById(id: Long): PipelineEntity?
    fun updateStatus(id: Long, status: PipelineStatus)
}