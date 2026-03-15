package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PromptType
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.persistence.entity.AnswersArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.*
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val promptRepository: PromptRepository,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<PipelineStepService>,
    private val questionCatalogClient: QuestionCatalogClient
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

        when (step.stepType) {
            "TOPIC_TREE_GENERATION" -> {
                val topicTreeArtifact = step.artifact as? TopicTreeArtifactEntity
                    ?: throw IllegalStateException("Topic tree artifact not found")
                topicTreeArtifact.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.ARTIFACT_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
                }
                artifactStorage.saveTopicTreeArtifact(existing.topicKey, name, yamlContent)
            }

            "QUESTIONS_GENERATION" -> {
                val answersArtifact = step.artifact as? AnswersArtifactEntity
                    ?: throw IllegalStateException("Questions artifact not found")
                answersArtifact.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.ARTIFACT_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
                }
                artifactStorage.saveAnswersArtifact(existing.topicKey, name, yamlContent)
            }

            "LONG_ANSWERS_GENERATION" -> {
                val answersArtifact = step.artifact as? AnswersArtifactEntity
                    ?: throw IllegalStateException("Answers artifact not found")
                answersArtifact.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.ARTIFACT_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
                }
                artifactStorage.saveAnswersArtifact(existing.topicKey, name, yamlContent)
            }

            "SHORT_ANSWERS_GENERATION" -> {
                val answersArtifact = step.artifact as? AnswersArtifactEntity
                    ?: throw IllegalStateException("Short answers artifact not found")
                answersArtifact.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.ARTIFACT_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
                }
                artifactStorage.saveShortAnswersArtifact(existing.topicKey, name, yamlContent)
            }

            else -> throw IllegalArgumentException("Unsupported step type: ${step.stepType}")
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

    @Transactional
    fun runStep(pipelineName: String, stepIndex: Int) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")

        val step = pipeline.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")

        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("PipelineStepService for type ${step.stepType} not found")

        pipeline.status = PipelineStatus.GENERATION_IN_PROGRESS
        pipeline.updatedAt = Instant.now()
        pipelineRepository.save(pipeline)

        try {
            generationStep.generate(step)
        } catch (e: Exception) {
            logger.error("Step '{}' failed for pipeline '{}': {}", step.stepType, pipelineName, e.message, e)
            pipeline.status = PipelineStatus.FAILED
            pipeline.updatedAt = Instant.now()
            pipelineRepository.save(pipeline)
            throw e
        }
    }

    @Transactional
    fun updatePipelineMetadata(name: String, topicKey: String?, steps: List<UpdatePipelineStepRequest>?): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

        if (topicKey != null) {
            existing.topicKey = topicKey
        }

        if (steps != null) {
            existing.steps.clear()
            existing.steps.addAll(steps.mapIndexed { index, stepRequest ->
                PipelineStepEntity(
                    pipeline = existing,
                    stepType = stepRequest.type,
                    stepOrder = index,
                    systemPrompt = getOrCreatePrompt(name, stepRequest.type, PromptType.SYSTEM, stepRequest.systemPromptName, stepRequest.systemPrompt),
                    userPrompt = getOrCreatePrompt(name, stepRequest.type, PromptType.USER, stepRequest.userPromptName, stepRequest.userPrompt)
                )
            })
        }

        existing.updatedAt = Instant.now()
        return pipelineRepository.save(existing).toResponse()
    }

    @Transactional
    fun runPipelineFrom(pipelineName: String, startStep: Int) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")
        val maxStep = pipeline.steps.size - 1
        for (stepIndex in startStep..maxStep) {
            if (stepIndex > startStep) {
                val previousStep = pipeline.steps[stepIndex - 1]
                val previousArtifact = previousStep.artifact
                if (previousArtifact == null || previousArtifact.status != ArtifactStatus.APPROVED) {
                    logger.info(
                        "Pipeline '{}' paused at step {}: previous artifact not approved (status: {})",
                        pipelineName, stepIndex, previousArtifact?.status
                    )
                    pipeline.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
                    pipeline.updatedAt = Instant.now()
                    pipelineRepository.save(pipeline)
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
                systemPrompt = getOrCreatePrompt(normalizedName, stepRequest.type, PromptType.SYSTEM, stepRequest.systemPromptName, stepRequest.systemPrompt),
                userPrompt = getOrCreatePrompt(normalizedName, stepRequest.type, PromptType.USER, stepRequest.userPromptName, stepRequest.userPrompt)
            )
        })

        return pipelineRepository.save(pipelineEntity).toResponse()
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

    private fun PipelineEntity.toResponse() = PipelineResponse(
        pipelineName = name,
        topicKey = topicKey,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
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
