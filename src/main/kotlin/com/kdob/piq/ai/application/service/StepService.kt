package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StepService(
    private val generationSteps: List<PipelineStepService>,
    private val pipelineService: PipelineService,
    private val pipelineStatusService: PipelineStatusService,
    private val loggerService: LoggerService
) {
    fun getAvailableStepTypes(): List<PipelineStepService> = generationSteps

    fun runStep(pipelineName: String, stepIndex: Int) {
        val pipeline = pipelineStatusService.toInProgress(pipelineName)
        val (pipelineStep, pipelineStepService) = pair(pipeline, stepIndex)
        runGenerationStep(pipelineStepService, pipelineStep)
    }

    private fun pair(
        pipeline: PipelineEntity,
        stepIndex: Int
    ): Pair<PipelineStepEntity, PipelineStepService> {
        val pipelineStep = pipeline.steps.getOrNull(stepIndex) ?: throw IllegalArgumentException("Step at index [$stepIndex] not found")
        val pipelineStepService = generationSteps.find { it.getStepType() == pipelineStep.stepType } ?: throw IllegalStateException("PipelineStepService for type [${pipelineStep.stepType}] not found")
        return Pair(pipelineStep, pipelineStepService)
    }

    private fun runGenerationStep(
        pipelineStepService: PipelineStepService,
        pipelineStep: PipelineStepEntity
    ) {
        try {
            pipelineStepService.generate(pipelineStep)
        } catch (e: Exception) {
            loggerService.log(pipelineStep, "Step [${pipelineStep.stepType}] failed for pipeline [${pipelineStep.pipeline.name}]. [${e.message}]")
            pipelineStatusService.toFailed(pipelineStep.pipeline)
            throw e
        }
    }

    fun runFrom(pipelineName: String, startStep: Int): PipelineEntity {
        val pipeline = pipelineService.get(pipelineName)
        val maxStep = pipeline.steps.size - 1
        for (stepIndex in startStep..maxStep) {
            if (stepIndex > startStep) {
                val previousStep = pipeline.steps[stepIndex - 1]
                val previousArtifact = previousStep.artifact
                if (previousArtifact == null || previousArtifact.status != ArtifactStatus.APPROVED) {
                    loggerService.log(pipeline,
                        "Pipeline [$pipelineName] paused at step [$stepIndex]. Previous artifact is not approved. Status: [${previousArtifact?.status}]"
                    )
                    return pipelineStatusService.toWaitingApproval(pipeline)
                }
            }
            runStep(pipelineName, stepIndex)
        }
        return pipeline
    }
}
