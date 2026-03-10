package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPipelineRepository: JpaRepository<PipelineEntity, Long> {
    @EntityGraph(attributePaths = ["steps", "topicsArtifact", "questionsArtifact"])
    override fun findAll(): MutableList<PipelineEntity>

    @EntityGraph(attributePaths = ["steps", "topicsArtifact", "questionsArtifact"])
    fun findByName(name: String): MutableList<PipelineEntity>

    fun deleteByName(name: String)
}