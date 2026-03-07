package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPipelineRepository: JpaRepository<PipelineEntity, Long> {
    fun findByName(name: String): MutableList<PipelineEntity>
}