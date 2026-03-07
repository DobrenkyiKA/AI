package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm


interface PipelineRepository {
    fun findAll(): List<PipelineEntity>
    fun save(pipeline: PipelineDefinitionForm): PipelineDefinitionForm
    fun save(pipeline: PipelineEntity): PipelineEntity
    fun findByName(name: String): PipelineEntity?
    fun updateStatus(name: String, status: PipelineStatus)
    fun deleteByName(name: String)
}