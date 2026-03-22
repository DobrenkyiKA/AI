package com.kdob.piq.ai.application.service.utility

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.ArtifactStatus.GENERATION_IN_PROGRESS
import com.kdob.piq.ai.domain.model.ArtifactStatus.PENDING_FOR_APPROVAL
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class PipelineArtifactStatusService(
    private val logger: LoggerService
) {
    @Transactional
    fun toInProgress(pipelineStep: PipelineStepEntity) {
        changeArtifactStatus(pipelineStep, GENERATION_IN_PROGRESS)
    }

    @Transactional
    fun toPendingApproval(pipelineStep: PipelineStepEntity) {
        changeArtifactStatus(pipelineStep, PENDING_FOR_APPROVAL)
    }

    private fun changeArtifactStatus(pipelineStep: PipelineStepEntity, status: ArtifactStatus) {
        val artifact = pipelineStep.artifact ?: return
        val oldStatus = artifact.status
        pipelineStep.artifact!!.status = status
        logger.log(pipelineStep, "Artifact status changed from [$oldStatus] to [${status}]")
    }
}