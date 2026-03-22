package com.kdob.piq.ai.application.service.logging

import com.kdob.piq.ai.infrastructure.persistence.GenerationLogRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.GenerationLogEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggerService(
    private val generationLogRepository: GenerationLogRepository
) {
    private val logger = LoggerFactory.getLogger(LoggerService::class.java)

    fun log(pipeline: PipelineEntity, message: String) {
        logger.info(message)
        generationLogRepository.save(GenerationLogEntity(pipeline, message))
    }

    fun log(pipelineStep: PipelineStepEntity, message: String) {
        logger.info("Pipeline: [${pipelineStep.pipeline.name}], Step: [$pipelineStep.stepOrder], Message: [$message]")
        generationLogRepository.save(GenerationLogEntity(pipelineStep.pipeline, message, pipelineStep.stepOrder))
    }
}