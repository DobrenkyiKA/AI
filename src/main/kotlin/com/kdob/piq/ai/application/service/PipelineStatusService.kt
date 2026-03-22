package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.service.logging.LoggerService
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PipelineStatus.*
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PipelineStatusService(
    private val pipelineService: PipelineService,
    private val loggerService: LoggerService
) {
    fun isStopped(pipelineStep: PipelineStepEntity): Boolean {
        loggerService.log(pipelineStep, "Pipeline status [${pipelineStep.pipeline.status}]. Step [${pipelineStep.stepOrder}].")
        return  pipelineStep.pipeline.status == PAUSED || pipelineStep.pipeline.status == ABORTED
    }

    fun toInProgress(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, GENERATION_IN_PROGRESS)

    fun toInProgress(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, GENERATION_IN_PROGRESS)

    fun toWaitingApproval(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, WAITING_ARTIFACT_APPROVAL)

    fun toWaitingApproval(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, WAITING_ARTIFACT_APPROVAL)

    fun toFailed(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, FAILED)

    fun toFailed(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, FAILED)

    fun toPaused(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PAUSED)

    fun toPaused(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PAUSED)

    fun toAborted(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, ABORTED)

    fun toAborted(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, ABORTED)

    fun toApproved(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, ARTIFACT_APPROVED)

    fun toApproved(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, ARTIFACT_APPROVED)

    private fun updatePipelineStatus(pipelineName: String, status: PipelineStatus): PipelineEntity {
        val pipelineEntity = pipelineService.get(pipelineName)
        return updatePipelineStatus(pipelineEntity, status)
    }

    private fun updatePipelineStatus(pipelineEntity: PipelineEntity, newStatus: PipelineStatus): PipelineEntity {
        val oldStatus = pipelineEntity.status
        pipelineEntity.status = newStatus
        pipelineEntity.updatedAt = Instant.now()
        val persistedPipeline = pipelineService.save(pipelineEntity)
        loggerService.log(
            persistedPipeline,
            "Pipeline [${pipelineEntity.name}] status changed from [$oldStatus] to [$newStatus]"
        )
        return persistedPipeline
    }
}

