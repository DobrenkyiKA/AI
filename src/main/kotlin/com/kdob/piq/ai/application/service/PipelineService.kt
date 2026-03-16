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
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val promptRepository: PromptRepository,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<PipelineStepService>,
    private val questionCatalogClient: QuestionCatalogClient,
    private val statusService: PipelineStatusService,
    private val generationLogRepository: GenerationLogRepository,
    private val promptSyncService: PromptSyncService
) {
    private val logger = LoggerFactory.getLogger(PipelineService::class.java)

    @Transactional(readOnly = true)
    fun findAll(): List<PipelineResponse> = pipelineRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun findByName(name: String): PipelineResponse? = pipelineRepository.findByName(name)?.toResponse()

    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> =
        generationSteps.map { PipelineStepTypeResponse(it.getStepType(), it.getLabel()) }

    fun getArtifact(name: String, stepIndex: Int): String {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")
        val step = existing.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")
        return artifactStorage.loadArtifact(existing.topicKey, name, step.stepType)
    }

    @Transactional
    fun updateArtifact(name: String, stepIndex: Int, yamlContent: String, status: ArtifactStatus): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

        val step = existing.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")

        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type ${step.stepType} not found")

        generationStep.updateArtifact(step, yamlContent, status)

        if (status == ArtifactStatus.APPROVED) {
            existing.status = PipelineStatus.ARTIFACT_APPROVED
        }

        existing.updatedAt = Instant.now()
        return pipelineRepository.save(existing).toResponse()
    }

    @Transactional
    fun deletePipeline(name: String) {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")
        pipelineRepository.deleteByName(name)
        artifactStorage.deleteArtifacts(existing.topicKey, name)
    }

    @Async
    fun runStep(pipelineName: String, stepIndex: Int) {
        statusService.updateStatus(pipelineName, PipelineStatus.GENERATION_IN_PROGRESS)

        val pipeline = statusService.getPipelineWithSteps(pipelineName)

        val step = pipeline.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")

        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type ${step.stepType} not found")

        try {
            generationStep.generate(step)
        } catch (e: Exception) {
            logger.error("Step '{}' failed for pipeline '{}': {}", step.stepType, pipelineName, e.message, e)
            statusService.updateStatus(pipelineName, PipelineStatus.FAILED)
            throw e
        }
    }

    @Transactional
    fun updatePipelineMetadata(
        name: String,
        topicKey: String? = null,
        steps: List<UpdatePipelineStepRequest>? = null
    ): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

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
        return pipelineRepository.save(existing).toResponse()
    }

    @Async
    fun runPipelineFrom(pipelineName: String, startStep: Int) {
        val pipeline = statusService.getPipelineWithSteps(pipelineName)
        val maxStep = pipeline.steps.size - 1
        for (stepIndex in startStep..maxStep) {
            if (stepIndex > startStep) {
                val currentPipeline = statusService.getPipelineWithSteps(pipelineName)
                val previousStep = currentPipeline.steps[stepIndex - 1]
                val previousArtifact = previousStep.artifact
                if (previousArtifact == null || previousArtifact.status != ArtifactStatus.APPROVED) {
                    logger.info(
                        "Pipeline '{}' paused at step {}: previous artifact not approved (status: {})",
                        pipelineName, stepIndex, previousArtifact?.status
                    )
                    statusService.updateStatus(pipelineName, PipelineStatus.WAITING_ARTIFACT_APPROVAL)
                    return
                }
            }
            runStep(pipelineName, stepIndex)
        }
    }

    @Transactional
    fun publishArtifact(pipelineName: String) {
        val existing = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")

        val topicTreeStep = existing.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }
            ?: throw IllegalStateException("TOPIC_TREE_GENERATION step not found for pipeline: $pipelineName")
        val topicTreeArtifact = topicTreeStep.artifact as? TopicTreeArtifactEntity
            ?: throw IllegalStateException("Topic tree artifact not found for pipeline: $pipelineName")

        if (topicTreeArtifact.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Topic tree artifact is not APPROVED. Current status: ${topicTreeArtifact.status}")
        }

        val rootTopic = questionCatalogClient.findTopic(existing.topicKey)
            ?: throw IllegalStateException("Root topic not found in catalog: ${existing.topicKey}")

        val nodesByParent = topicTreeArtifact.nodes.groupBy { it.parentTopicKey }

        fun publishRecursive(parentKey: String, parentPath: String) {
            val children = nodesByParent[parentKey] ?: return
            for (child in children) {
                val request = CreateTopicClientRequest(
                    key = child.key,
                    name = child.name,
                    parentPath = parentPath,
                    coverageArea = child.coverageArea
                )
                val response = questionCatalogClient.createTopic(request)
                publishRecursive(child.key, response.path)
            }
        }

        publishRecursive(existing.topicKey, rootTopic.path)
    }

    @Transactional
    fun createPipeline(name: String, topicKey: String, steps: List<CreatePipelineStepRequest>): PipelineResponse {
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

        val response = pipelineRepository.save(pipelineEntity).toResponse()
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
    fun pausePipeline(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")
        pipeline.status = PipelineStatus.PAUSED
        pipeline.steps.find { it.artifact != null && it.artifact?.status == ArtifactStatus.PENDING_FOR_APPROVAL }
            ?.let { it.artifact?.status = ArtifactStatus.PAUSED }
        pipelineRepository.save(pipeline)
    }

    @Transactional
    fun abortPipeline(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")
        pipeline.status = PipelineStatus.ABORTED
        pipeline.steps.find { it.artifact != null && (it.artifact?.status == ArtifactStatus.PENDING_FOR_APPROVAL || it.artifact?.status == ArtifactStatus.PAUSED) }
            ?.let { step ->
                step.artifact?.status = ArtifactStatus.ABORTED
                artifactStorage.deleteArtifact(pipeline.topicKey, pipelineName, step.stepType)
            }
        pipelineRepository.save(pipeline)
    }

    @Transactional
    fun removeArtifact(pipelineName: String, stepIndex: Int): PipelineResponse {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")
        val step = pipeline.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step index $stepIndex not found")

        if (step.artifact != null) {
            artifactStorage.deleteArtifact(pipeline.topicKey, pipelineName, step.stepType)
            step.artifact = null
            pipeline.updatedAt = Instant.now()
            return pipelineRepository.save(pipeline).toResponse()
        }
        return pipeline.toResponse()
    }

    private fun PipelineEntity.toResponse(): PipelineResponse {
        val logs = generationLogRepository.findByPipelineNameOrderByCreatedAtAsc(name)
            .map { GenerationLogResponse(it.message, it.stepOrder, it.createdAt) }

        return PipelineResponse(
            pipelineName = name,
            topicKey = topicKey,
            status = status.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            logs = logs,
            steps = steps.mapIndexed { index, step ->
                PipelineStepResponse(
                    step = index,
                    type = step.stepType,
                    status = step.artifact?.status,
                    systemPromptName = step.systemPrompt?.name,
                    systemPrompt = step.systemPrompt?.content ?: "",
                    userPromptName = step.userPrompt?.name,
                    userPrompt = step.userPrompt?.content ?: ""
                )
            }
        )
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
