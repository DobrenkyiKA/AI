package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity


interface PipelineRepository {
    fun findAll(): List<PipelineEntity>
    fun save(pipeline: PipelineEntity): PipelineEntity
    fun saveAndFlush(pipeline: PipelineEntity): PipelineEntity
    fun findByName(name: String): PipelineEntity?
    fun deleteByName(name: String)
}