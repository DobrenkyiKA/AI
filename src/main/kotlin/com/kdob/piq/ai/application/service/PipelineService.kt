package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.GenerationLogEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
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
    private val promptRepository: PromptRepository,
    private val artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    private val promptSyncService: PromptSyncService
) {
    private val logger = LoggerFactory.getLogger(PipelineService::class.java)

    private fun getPipelineEntity(name: String): PipelineEntity =
        pipelineRepository.findByName(name) ?: throw NoSuchElementException("Pipeline not found: $name")

    @Transactional(readOnly = true)
    fun get(name: String): PipelineEntity = getPipelineEntity(name)

    @Transactional(readOnly = true)
    fun getAll(): List<PipelineEntity> = pipelineRepository.findAll()


    fun save(pipeline: PipelineEntity): PipelineEntity = pipelineRepository.save(pipeline)

    @Transactional
    fun deletePipeline(name: String) {
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
                    existingStep.systemPrompt = getOrCreatePrompt(
                        name,
                        stepRequest.type,
                        PromptType.SYSTEM,
                        stepRequest.systemPromptName,
                        stepRequest.systemPrompt
                    )
                    existingStep.userPrompt = getOrCreatePrompt(
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
                        systemPrompt = getOrCreatePrompt(
                            name,
                            stepRequest.type,
                            PromptType.SYSTEM,
                            stepRequest.systemPromptName,
                            stepRequest.systemPrompt
                        ),
                        userPrompt = getOrCreatePrompt(
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
        val normalizedName = normalizeAndValidateName(name)
        val pipelineEntity = PipelineEntity(name = normalizedName, topicKey = topicKey)

        pipelineEntity.steps.addAll(steps.mapIndexed { index, stepRequest ->
            PipelineStepEntity(
                pipeline = pipelineEntity,
                stepType = stepRequest.type,
                stepOrder = index,
                systemPrompt = getOrCreatePrompt(
                    normalizedName,
                    stepRequest.type,
                    PromptType.SYSTEM,
                    stepRequest.systemPromptName,
                    stepRequest.systemPrompt
                ),
                userPrompt = getOrCreatePrompt(
                    normalizedName,
                    stepRequest.type,
                    PromptType.USER,
                    stepRequest.userPromptName,
                    stepRequest.userPrompt
                )
            )
        })

        val response = pipelineRepository.save(pipelineEntity)
        promptSyncService.exportToNewVersion("Auto-export after creating pipeline: $normalizedName")
        return response
    }

    private fun normalizeAndValidateName(name: String): String {
        if (name.isBlank()) {
            throw IllegalArgumentException("Pipeline name cannot be empty")
        }
        val normalized = name.trim().replace("\\s+".toRegex(), "-").lowercase()
        if (!normalized.matches("^[a-z0-9-]+$".toRegex())) {
            throw IllegalArgumentException("Pipeline name can only contain lowercase alphanumeric characters and '-'")
        }
        if (pipelineRepository.findByName(normalized) != null) {
            throw IllegalArgumentException("Pipeline with name $normalized already exists")
        }
        return normalized
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

    private fun getOrCreatePrompt(
        pipelineName: String,
        stepType: String,
        type: PromptType,
        providedName: String?,
        providedContent: String?
    ): PromptEntity {
        if (!providedName.isNullOrBlank()) {
            val existing = promptRepository.findByName(providedName)
            if (existing != null) {
                if (!providedContent.isNullOrBlank() && providedContent != existing.content) {
                    existing.content = providedContent
                    return promptRepository.save(existing)
                }
                return existing
            }
        }

        if (!providedContent.isNullOrBlank()) {
            val promptName = providedName ?: "$pipelineName-$stepType-${type.name.lowercase()}"
            val existing = promptRepository.findByName(promptName)
            return if (existing != null) {
                existing.content = providedContent
                promptRepository.save(existing)
            } else {
                promptRepository.save(PromptEntity(type = type, name = promptName, content = providedContent))
            }
        }

        val defaultPromptName = "DEFAULT_${stepType}_${type.name}"
        return promptRepository.findByName(defaultPromptName)
            ?: throw IllegalStateException("Default prompt not found: $defaultPromptName. Make sure default prompts are loaded on startup.")
    }
}
