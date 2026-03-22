package com.kdob.piq.ai.application.service.utility

import com.kdob.piq.ai.domain.model.ArtifactStatus.GENERATION_IN_PROGRESS
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class PipelineArtifactStatusService(
    private val logger: LoggerService
) {

    @Transactional
    fun toInProgress(pipelineStep: PipelineStepEntity) {
        val artifact = pipelineStep.artifact ?: return
        val oldStatus = artifact.status
        pipelineStep.artifact!!.status = GENERATION_IN_PROGRESS
        logger.log(pipelineStep, "Artifact status changed from [$oldStatus] to [${GENERATION_IN_PROGRESS}]")
    }
}