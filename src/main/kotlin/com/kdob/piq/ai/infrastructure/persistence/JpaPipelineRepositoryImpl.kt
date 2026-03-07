package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toDomain
import com.kdob.piq.ai.infrastructure.persistence.mapping.toEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JpaPipelineRepositoryImpl (
    private val springDataPipelineRepository: SpringDataPipelineRepository
): PipelineRepository {
    override fun findAll(): List<PipelineEntity> = springDataPipelineRepository.findAll()
    override fun save(pipeline: PipelineDefinitionForm) = springDataPipelineRepository.save(pipeline.toEntity()).toDomain()
    override fun save(pipeline: PipelineEntity): PipelineEntity = springDataPipelineRepository.save(pipeline)
    override fun saveAndFlush(pipeline: PipelineEntity): PipelineEntity = springDataPipelineRepository.saveAndFlush(pipeline)
    override fun findByName(name: String): PipelineEntity? = springDataPipelineRepository.findByName(name).getOrNull(0)
    override fun deleteByName(name: String) {
        springDataPipelineRepository.deleteByName(name)
    }
}