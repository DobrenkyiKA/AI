package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm


interface PipelineRepository {

    fun save(pipeline: PipelineDefinitionForm): PipelineDefinitionForm
    fun findByName(name: String): PipelineEntity?
    fun updateStatus(name: String, status: PipelineStatus)
}