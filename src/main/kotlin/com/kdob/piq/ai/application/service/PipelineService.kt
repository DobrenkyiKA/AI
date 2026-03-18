package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.prompt.PromptSyncService
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
import com.kdob.piq.ai.infrastructure.web.dto.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val promptRepository: PromptRepository,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<PipelineStepService>,
    private val generationLogRepository: GenerationLogRepository,
    private val promptSyncService: PromptSyncService
) {
    private val logger = LoggerFactory.getLogger(PipelineService::class.java)

    private fun getPipelineEntity(name: String): PipelineEntity =
        pipelineRepository.findByName(name) ?: throw NoSuchElementException("Pipeline not found: $name")

    fun get(name: String): PipelineEntity = getPipelineEntity(name)

    fun getAll(): List<PipelineEntity> = pipelineRepository.findAll()

    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> =
        generationSteps.map { PipelineStepTypeResponse(it.getStepType(), it.getLabel()) }

    fun save(pipeline: PipelineEntity): PipelineEntity = pipelineRepository.save(pipeline)

    @Transactional
    fun deletePipeline(name: String) {
        val existing = getPipelineEntity(name)
        pipelineRepository.deleteByName(name)
        artifactStorage.deleteArtifacts(existing.topicKey, name)
    }

    @Async
    fun runStep(pipelineName: String, stepIndex: Int) {
        updateStatus(pipelineName, PipelineStatus.GENERATION_IN_PROGRESS)

        val pipeline = getPipelineWithSteps(pipelineName)

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
    private fun updateStatus(pipelineName: String, status: PipelineStatus) {
        val pipeline = get(pipelineName)
        pipeline.status = status
        pipeline.updatedAt = Instant.now()
        pipelineRepository.save(pipeline)
    }
    private fun getPipelineWithSteps(pipelineName: String): PipelineEntity {
        val pipeline = get(pipelineName)
        pipeline.steps.size // Force load
        pipeline.steps.forEach { it.artifact?.status } // Force load artifacts and their status
        return pipeline
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

    fun runFrom(pipelineName: String, startStep: Int): PipelineEntity {
        val pipeline = getPipelineWithSteps(pipelineName)
        val maxStep = pipeline.steps.size - 1
        for (stepIndex in startStep..maxStep) {
            if (stepIndex > startStep) {
                val currentPipeline = getPipelineWithSteps(pipelineName)
                val previousStep = currentPipeline.steps[stepIndex - 1]
                val previousArtifact = previousStep.artifact
                if (previousArtifact == null || previousArtifact.status != ArtifactStatus.APPROVED) {
                    logger.info(
                        "Pipeline [{}] paused at step [{}]: previous artifact not approved (status: [{}])",
                        pipelineName, stepIndex, previousArtifact?.status
                    )
                    updateStatus(pipelineName, PipelineStatus.WAITING_ARTIFACT_APPROVAL)
                    return getPipelineWithSteps(pipelineName)
                }
            }
            runStep(pipelineName, stepIndex)
        }
        return getPipelineWithSteps(pipelineName)
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
                generationLogRepository.save(GenerationLogEntity(pipeline, "Generation PAUSED by user.", step.stepOrder))
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
                generationLogRepository.save(GenerationLogEntity(pipeline, "Generation ABORTED by user.", step.stepOrder))
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



    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
}
