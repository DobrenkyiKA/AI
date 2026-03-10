package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.topics.TopicsArtifactValidator
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.TopicsArtifactForm
import com.kdob.piq.ai.infrastructure.web.dto.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
    private val generationSteps: List<GenerationStep>,
    private val questionCatalogClient: QuestionCatalogClient
) {
    @Transactional(readOnly = true)
    fun findAll(): List<PipelineResponse> = pipelineRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun findByName(name: String): PipelineResponse? = pipelineRepository.findByName(name)?.toResponse()

    fun getArtifact(name: String, stepIndex: Int): String {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")
        val step = existing.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")
        return artifactStorage.loadArtifact(name, step.stepType)
    }

    fun getTopicsArtifact(name: String): String = artifactStorage.loadTopicsArtifact(name)

    @Transactional
    fun updateArtifact(name: String, stepIndex: Int, yamlContent: String, status: ArtifactStatus): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

        val step = existing.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")

        when (step.stepType) {
            "TOPICS_GENERATION" -> {
                val updatedForm = yamlMapper.readValue(yamlContent, TopicsArtifactForm::class.java)
                TopicsArtifactValidator.validate(updatedForm)

                existing.questionsArtifact = null
                artifactStorage.deleteArtifact(name, "QUESTIONS_GENERATION")

                if (existing.topicsArtifact != null) {
                    existing.topicsArtifact = null
                    pipelineRepository.saveAndFlush(existing)
                }

                val topicsArtifact = TopicsArtifactEntity(pipeline = existing)
                topicsArtifact.status = status
                topicsArtifact.topics.addAll(updatedForm.topics.map { it.toEntity(topicsArtifact) })
                existing.topicsArtifact = topicsArtifact

                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.DRAFT
                }
                artifactStorage.saveTopicsArtifact(name, yamlContent)
            }

            "QUESTIONS_GENERATION" -> {
                val questionsArtifact = existing.questionsArtifact ?: throw IllegalStateException("Questions artifact not found")
                questionsArtifact.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.QUESTIONS_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.QUESTIONS_PENDING_FOR_APPROVAL
                }
                artifactStorage.saveQuestionsArtifact(name, yamlContent)
            }

            else -> throw IllegalArgumentException("Unsupported step type: ${step.stepType}")
        }

        existing.updatedAt = Instant.now()
        return pipelineRepository.save(existing).toResponse()
    }

    @Transactional
    fun updatePipeline(name: String, yamlContent: String): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")
        val topicsStepIndex = existing.steps.indexOfFirst { it.stepType == "TOPICS_GENERATION" }
        if (topicsStepIndex == -1) throw IllegalStateException("Topics generation step not found")

        return updateArtifact(
            name,
            topicsStepIndex,
            yamlContent,
            existing.topicsArtifact?.status ?: ArtifactStatus.PENDING_FOR_APPROVAL
        )
    }

    @Transactional
    fun deletePipeline(name: String) {
        pipelineRepository.deleteByName(name)
        artifactStorage.deleteArtifacts(name)
    }

    @Transactional
    fun runStep(pipelineName: String, stepIndex: Int) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")

        val step = pipeline.steps.getOrNull(stepIndex)
            ?: throw IllegalArgumentException("Step at index $stepIndex not found")

        val generationStep = generationSteps.find { it.getStepType() == step.stepType }
            ?: throw IllegalStateException("GenerationStep for type ${step.stepType} not found")

        generationStep.generate(pipeline, step)
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
                    systemPrompt = stepRequest.systemPrompt,
                    userPrompt = stepRequest.userPrompt
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
        for (step in startStep..maxStep) {
            runStep(pipelineName, step)
        }
    }

    @Transactional
    fun publishTopicsArtifact(pipelineName: String) {
        val existing = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")

        val topicsArtifact = existing.topicsArtifact
            ?: throw IllegalStateException("Topics artifact not found for pipeline: $pipelineName")

        if (topicsArtifact.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Topics artifact is not APPROVED. Current status: ${topicsArtifact.status}")
        }

        val rootTopic = questionCatalogClient.findTopic(existing.topicKey)
            ?: throw IllegalStateException("Root topic not found in catalog: ${existing.topicKey}")

        val topicsByParent = topicsArtifact.topics.groupBy { it.parentTopicKey }

        fun publishRecursive(parentKey: String, parentPath: String) {
            val children = topicsByParent[parentKey] ?: return
            for (child in children) {
                val request = CreateTopicClientRequest(
                    key = child.key,
                    name = child.name,
                    parentPath = parentPath,
                    coverageArea = child.coverageArea,
                    exclusions = "" // Artifact topics don't have exclusions, they are filtered out during generation
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
                systemPrompt = stepRequest.systemPrompt,
                userPrompt = stepRequest.userPrompt
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
                status = when (step.stepType) {
                    "TOPICS_GENERATION" -> topicsArtifact?.status
                    "QUESTIONS_GENERATION" -> questionsArtifact?.status
                    else -> null
                },
                systemPrompt = step.systemPrompt,
                userPrompt = step.userPrompt
            )
        }
    )

    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
}