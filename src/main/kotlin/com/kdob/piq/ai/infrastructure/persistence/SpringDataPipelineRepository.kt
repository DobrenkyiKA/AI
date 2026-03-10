package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPipelineRepository: JpaRepository<PipelineEntity, Long> {
    @EntityGraph(attributePaths = ["steps", "artifactStep0", "artifactStep1"])
    override fun findAll(): MutableList<PipelineEntity>

    @EntityGraph(attributePaths = ["steps", "artifactStep0", "artifactStep1"])
    fun findByName(name: String): MutableList<PipelineEntity>

    fun deleteByName(name: String)
}