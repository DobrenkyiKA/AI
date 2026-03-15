package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineStatusService(
    private val pipelineRepository: PipelineRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateStatus(pipelineName: String, status: PipelineStatus) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")
        pipeline.status = status
        pipeline.updatedAt = Instant.now()
        pipelineRepository.save(pipeline)
    }

    @Transactional(readOnly = true)
    fun getPipelineWithSteps(pipelineName: String): PipelineEntity {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")
        pipeline.steps.size // Force load
        return pipeline
    }
}
