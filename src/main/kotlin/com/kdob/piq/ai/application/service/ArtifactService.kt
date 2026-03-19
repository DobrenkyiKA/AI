package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.time.Instant
import kotlin.text.get

@Service
class ArtifactService(
    val pipelineService: PipelineService,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<PipelineStepService>,
    private val questionCatalogClient: QuestionCatalogClient
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
        val step = pipeline.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step index $stepIndex not found")

        if (step.artifact != null) {
            artifactStorage.deleteArtifact(pipeline.topicKey, pipelineName, step.stepType)
            step.artifact = null
            pipeline.updatedAt = Instant.now()
            return pipelineService.save(pipeline)
        }
        return pipeline
    }
}