package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ArtifactService(
    private val pipelineService: PipelineService,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<PipelineStepService>,
    private val pipelineStatusService: PipelineStatusService
) {
    @Transactional
    fun update(name: String, stepIndex: Int, yamlContent: String, status: ArtifactStatus): PipelineEntity {
        val existing = pipelineService.get(name)
        val step = getStep(existing, stepIndex)
        val generationStep = getGenerationStep(step)
        generationStep.updateArtifact(step, yamlContent, status)
        if (status == ArtifactStatus.APPROVED) {
            pipelineStatusService.toApproved(existing)
        }
        return pipelineService.save(existing)
    }

    private fun getStep(
        existing: PipelineEntity,
        stepIndex: Int
    ): PipelineStepEntity =
        existing.steps.getOrNull(stepIndex) ?: throw IllegalArgumentException("Step at index $stepIndex not found")

    private fun getGenerationStep(step: PipelineStepEntity): PipelineStepService =
        (generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type ${step.stepType.name} not found"))

    fun get(name: String, stepIndex: Int): String {
        val existing = pipelineService.get(name)
        val step = getStep(existing, stepIndex)
        return artifactStorage.loadArtifact(existing.topicKey, name, step.stepType)
    }

    @Transactional
    fun load(name: String, stepIndex: Int): PipelineEntity {
        val yaml = get(name, stepIndex)
        return update(name, stepIndex, yaml, ArtifactStatus.PENDING_FOR_APPROVAL)
    }

    @Transactional
    fun remove(pipelineName: String, stepIndex: Int): PipelineEntity {
        val pipeline = pipelineService.get(pipelineName)
        val step = pipeline.steps.getOrNull(stepIndex) ?: return pipeline

        if (step.artifact != null) {
            artifactStorage.deleteArtifact(pipeline.topicKey, pipelineName, step.stepType)
            step.artifact = null
            pipeline.updatedAt = Instant.now()
            return pipelineService.save(pipeline)
        }
        return pipeline
    }
}