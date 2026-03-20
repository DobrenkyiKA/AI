package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StepService(
    private val generationSteps: List<PipelineStepService>,
    private val pipelineService: PipelineService
) {
    private val logger = LoggerFactory.getLogger(StepService::class.java)
    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> =
        generationSteps.map { PipelineStepTypeResponse(it.getStepType(), it.getLabel()) }

    fun runStep(pipelineName: String, stepIndex: Int) {
        val pipeline = pipelineService.get(pipelineName)
        updateStatus(pipeline, PipelineStatus.GENERATION_IN_PROGRESS)


        val (step, generationStep) = pair(pipeline, stepIndex)

        try {
            generationStep.generate(step)
        } catch (e: Exception) {
            logger.error("Step [{}] failed for pipeline [{}]. [{}]", step.stepType, pipelineName, e.message, e)
            updateStatus(pipeline, PipelineStatus.FAILED)
            throw e
        }
    }

    private fun pair(
        pipeline: PipelineEntity,
        stepIndex: Int
    ): Pair<PipelineStepEntity, PipelineStepService> {
        val step = pipeline.steps.getOrNull(stepIndex) ?: throw IllegalArgumentException("Step at index [$stepIndex] not found")
        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type [${step.stepType}] not found")
        return Pair(step, generationStep)
    }

    fun runFrom(pipelineName: String, startStep: Int): PipelineEntity {
        val pipeline = pipelineService.get(pipelineName)
        val maxStep = pipeline.steps.size - 1
        for (stepIndex in startStep..maxStep) {
            if (stepIndex > startStep) {
                val currentPipeline = pipelineService.get(pipelineName)
                val previousStep = currentPipeline.steps[stepIndex - 1]
                val previousArtifact = previousStep.artifact
                if (previousArtifact == null || previousArtifact.status != ArtifactStatus.APPROVED) {
                    logger.info(
                        "Pipeline [{}] paused at step [{}]. Previous artifact is not approved. Status: [{}]",
                        pipelineName, stepIndex, previousArtifact?.status
                    )
                    updateStatus(pipelineName, PipelineStatus.WAITING_ARTIFACT_APPROVAL)
                    return pipelineService.get(pipelineName)
                }
            }
            runStep(pipelineName, stepIndex)
        }
        return pipelineService.get(pipelineName)
    }

    private fun updateStatus(pipeline: PipelineEntity, status: PipelineStatus) {
        pipeline.status = status
        pipeline.updatedAt = Instant.now()
        pipelineService.save(pipeline)
    }
}
