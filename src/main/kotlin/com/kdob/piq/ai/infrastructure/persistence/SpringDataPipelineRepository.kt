package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataPipelineRepository: JpaRepository<PipelineEntity, Long> {
    @EntityGraph(attributePaths = ["steps", "steps.artifact", "steps.systemPrompt", "steps.userPrompt"])
    override fun findAll(): MutableList<PipelineEntity>

    @EntityGraph(attributePaths = ["steps", "steps.artifact", "steps.systemPrompt", "steps.userPrompt"])
    fun findByName(name: String): PipelineEntity?

    fun deleteByName(name: String)
}