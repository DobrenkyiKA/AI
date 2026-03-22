package com.kdob.piq.ai.application.service.utility

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PipelineStatusService(
    private val pipelineService: PipelineService,
    private val loggerService: LoggerService
) {
    fun isStopped(pipelineStep: PipelineStepEntity): Boolean {
        loggerService.log(pipelineStep, "Pipeline status [${pipelineStep.pipeline.status}]. Step [${pipelineStep.stepOrder}].")
        return  pipelineStep.pipeline.status == PipelineStatus.PAUSED || pipelineStep.pipeline.status == PipelineStatus.ABORTED
    }

    fun isNotStopped(pipelineStep: PipelineStepEntity): Boolean = !isStopped(pipelineStep)

    @Transactional
    fun toInProgress(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PipelineStatus.GENERATION_IN_PROGRESS)

    @Transactional
    fun toInProgress(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PipelineStatus.GENERATION_IN_PROGRESS)

    @Transactional
    fun toWaitingApproval(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PipelineStatus.WAITING_ARTIFACT_APPROVAL)

    @Transactional
    fun toWaitingApproval(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PipelineStatus.WAITING_ARTIFACT_APPROVAL)

    @Transactional
    fun toFailed(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PipelineStatus.FAILED)

    @Transactional
    fun toFailed(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PipelineStatus.FAILED)

    @Transactional
    fun toPaused(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PipelineStatus.PAUSED)

    @Transactional
    fun toPaused(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PipelineStatus.PAUSED)

    @Transactional
    fun toAborted(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PipelineStatus.ABORTED)

    @Transactional
    fun toAborted(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PipelineStatus.ABORTED)

    @Transactional
    fun toApproved(pipelineEntity: PipelineEntity): PipelineEntity =
        updatePipelineStatus(pipelineEntity, PipelineStatus.ARTIFACT_APPROVED)

    @Transactional
    fun toApproved(pipelineName: String): PipelineEntity =
        updatePipelineStatus(pipelineName, PipelineStatus.ARTIFACT_APPROVED)

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