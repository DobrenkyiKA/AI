package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
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
        updateStatus(pipelineName, PipelineStatus.GENERATION_IN_PROGRESS)

        val pipeline = pipelineService.get(pipelineName)

        val step = pipeline.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")

        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type ${step.stepType} not found")

        try {
            generationStep.generate(step)
        } catch (e: Exception) {
            logger.error("Step '{}' failed for pipeline '{}': {}", step.stepType, pipelineName, e.message, e)
            updateStatus(pipelineName, PipelineStatus.FAILED)
            throw e
        }
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
                        "Pipeline [{}] paused at step [{}]: previous artifact not approved (status: [{}])",
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

    private fun updateStatus(pipelineName: String, status: PipelineStatus) {
        val pipeline = pipelineService.get(pipelineName)
        pipeline.status = status
        pipeline.updatedAt = Instant.now()
        pipelineService.save(pipeline)
    }
//
//    private fun getPipelineWithSteps(pipelineName: String): PipelineEntity {
//        val pipeline = getPipelineEntity(pipelineName)
//        pipeline.steps.size // Force load
//        pipeline.steps.forEach { it.artifact?.status } // Force load artifacts and their status
//        return pipeline
//    }
}
