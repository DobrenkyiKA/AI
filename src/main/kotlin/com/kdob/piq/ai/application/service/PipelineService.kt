package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.GenerationLogEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineStepRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val promptService: PromptService,
    private val artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    private val promptSyncService: PromptSyncService
) {
    private val logger = LoggerFactory.getLogger(PipelineService::class.java)

    @Transactional(readOnly = true)
    fun get(name: String): PipelineEntity = getPipelineEntity(name)

    private fun getPipelineEntity(name: String): PipelineEntity =
        pipelineRepository.findByName(name) ?: throw NoSuchElementException("Pipeline not found: $name")

    @Transactional(readOnly = true)
    fun getAll(): List<PipelineEntity> = pipelineRepository.findAll()


    fun save(pipeline: PipelineEntity): PipelineEntity = pipelineRepository.save(pipeline)

    @Transactional
    fun delete(name: String) {
        val existing = getPipelineEntity(name)
        pipelineRepository.deleteByName(name)
        artifactStorage.deleteArtifacts(existing.topicKey, name)
    }

    @Transactional
    fun updateMetadata(
        name: String,
        topicKey: String? = null,
        steps: List<UpdatePipelineStepRequest>? = null
    ): PipelineEntity {
        val existing = getPipelineEntity(name)

        if (topicKey != null) {
            existing.topicKey = topicKey
        }

        if (steps != null) {
            val updatedSteps = steps.mapIndexed { index, stepRequest ->
                val existingStep = existing.steps.getOrNull(index)
                if (existingStep != null && existingStep.stepType == stepRequest.type) {
                    existingStep.stepOrder = index
                    existingStep.systemPrompt = promptService.getOrCreatePrompt(
                        name,
                        stepRequest.type,
                        PromptType.SYSTEM,
                        stepRequest.systemPromptName,
                        stepRequest.systemPrompt
                    )
                    existingStep.userPrompt = promptService.getOrCreatePrompt(
                        name,
                        stepRequest.type,
                        PromptType.USER,
                        stepRequest.userPromptName,
                        stepRequest.userPrompt
                    )
                    existingStep
                } else {
                    PipelineStepEntity(
                        pipeline = existing,
                        stepType = stepRequest.type,
                        stepOrder = index,
                        systemPrompt = promptService.getOrCreatePrompt(
                            name,
                            stepRequest.type,
                            PromptType.SYSTEM,
                            stepRequest.systemPromptName,
                            stepRequest.systemPrompt
                        ),
                        userPrompt = promptService.getOrCreatePrompt(
                            name,
                            stepRequest.type,
                            PromptType.USER,
                            stepRequest.userPromptName,
                            stepRequest.userPrompt
                        )
                    )
                }
            }
            existing.steps.clear()
            existing.steps.addAll(updatedSteps)
            promptSyncService.exportToNewVersion("Auto-export after updating pipeline metadata: $name")
        }

        existing.updatedAt = Instant.now()
        return pipelineRepository.save(existing)
    }

    @Transactional
    fun create(name: String, topicKey: String, steps: List<CreatePipelineStepRequest>): PipelineEntity {
        if (pipelineRepository.findByName(name) != null) {
            throw IllegalArgumentException("Pipeline with name $name already exists")
        }
        val pipelineEntity = PipelineEntity(name = name, topicKey = topicKey)

        pipelineEntity.steps.addAll(steps.mapIndexed { index, stepRequest ->
            PipelineStepEntity(
                pipeline = pipelineEntity,
                stepType = stepRequest.type,
                stepOrder = index,
                systemPrompt = promptService.getOrCreatePrompt(
                    name,
                    stepRequest.type,
                    PromptType.SYSTEM,
                    stepRequest.systemPromptName,
                    stepRequest.systemPrompt
                ),
                userPrompt = promptService.getOrCreatePrompt(
                    name,
                    stepRequest.type,
                    PromptType.USER,
                    stepRequest.userPromptName,
                    stepRequest.userPrompt
                )
            )
        })

        val response = pipelineRepository.save(pipelineEntity)
        promptSyncService.exportToNewVersion("Auto-export after creating pipeline: $name")
        return response
    }

    @Transactional
    fun pause(pipelineName: String): PipelineEntity {
        val pipeline = getPipelineEntity(pipelineName)
        pipeline.status = PipelineStatus.PAUSED
        pipeline.steps.find { it.artifact != null && it.artifact?.status == ArtifactStatus.PENDING_FOR_APPROVAL }
            ?.let { step ->
                step.artifact?.status = ArtifactStatus.PAUSED
                generationLogRepository.save(
                    GenerationLogEntity(
                        pipeline,
                        "Generation PAUSED by user.",
                        step.stepOrder
                    )
                )
            }
        return pipelineRepository.save(pipeline)
    }

    @Transactional
    fun abort(pipelineName: String): PipelineEntity {
        val pipeline = getPipelineEntity(pipelineName)
        pipeline.status = PipelineStatus.ABORTED
        pipeline.steps.find { it.artifact != null && (it.artifact?.status == ArtifactStatus.PENDING_FOR_APPROVAL || it.artifact?.status == ArtifactStatus.PAUSED) }
            ?.let { step ->
                step.artifact?.status = ArtifactStatus.ABORTED
                artifactStorage.deleteArtifact(pipeline.topicKey, pipelineName, step.stepType)
                generationLogRepository.save(
                    GenerationLogEntity(
                        pipeline,
                        "Generation ABORTED by user.",
                        step.stepOrder
                    )
                )
            }
        return pipelineRepository.save(pipeline)
    }
}