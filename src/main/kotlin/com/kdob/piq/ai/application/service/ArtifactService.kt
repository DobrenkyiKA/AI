package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ArtifactService(
    val pipelineService: PipelineService,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<PipelineStepService>,
) {
    @Transactional
    fun update(name: String, stepIndex: Int, yamlContent: String, status: ArtifactStatus): PipelineEntity {
        val existing = pipelineService.get(name)
        val step =
            existing.steps.getOrNull(stepIndex) ?: throw IllegalArgumentException("Step at index $stepIndex not found")
        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type ${step.stepType} not found")
        generationStep.updateArtifact(step, yamlContent, status)
        if (status == ArtifactStatus.APPROVED) {
            existing.status = PipelineStatus.ARTIFACT_APPROVED
        }
        existing.updatedAt = Instant.now()
        return pipelineService.save(existing)
    }

    fun get(name: String, stepIndex: Int): String {
        val existing = pipelineService.get(name)
        val step =
            existing.steps.getOrNull(stepIndex) ?: throw IllegalArgumentException("Step at index $stepIndex not found")
        return artifactStorage.loadArtifact(existing.topicKey, name, step.stepType)
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