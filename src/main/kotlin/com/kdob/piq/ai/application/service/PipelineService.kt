package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.step0.Step0ArtifactValidator
import com.kdob.piq.ai.application.service.step0.Step0TopicsGenerationService
import com.kdob.piq.ai.application.service.step1.Step1QuestionGenerationService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.Step0ArtifactForm
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

    fun getArtifact(name: String, step: Int): String = artifactStorage.loadArtifact(name, step)

    fun getPipelineArtifact(name: String): String = getArtifact(name, 0)

    @Transactional
    fun updateArtifact(name: String, step: Int, yamlContent: String, status: ArtifactStatus): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

        when (step) {
            0 -> {
                val updatedForm = yamlMapper.readValue(yamlContent, Step0ArtifactForm::class.java)
                Step0ArtifactValidator.validate(updatedForm)

                existing.artifactStep1 = null
                artifactStorage.deleteArtifact(name, 1)

                if (existing.artifactStep0 != null) {
                    existing.artifactStep0 = null
                    pipelineRepository.saveAndFlush(existing)
                }

                val artifactStep0 = ArtifactStep0Entity(pipeline = existing)
                artifactStep0.status = status
                artifactStep0.topics.addAll(updatedForm.topics.map { it.toEntity(artifactStep0) })
                existing.artifactStep0 = artifactStep0

                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.DRAFT
                }
                artifactStorage.saveArtifact(name, 0, yamlContent)
            }

            1 -> {
                val artifactStep1 = existing.artifactStep1 ?: throw IllegalStateException("Step 1 artifact not found")
                artifactStep1.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.STEP_1_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.STEP_1_PENDING_FOR_APPROVAL
                }
                artifactStorage.saveArtifact(name, 1, yamlContent)
            }

            else -> throw IllegalArgumentException("Unsupported step: $step")
        }

        existing.updatedAt = Instant.now()
        return pipelineRepository.save(existing).toResponse()
    }

    @Transactional
    fun updatePipeline(name: String, yamlContent: String): PipelineResponse {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")
        return updateArtifact(
            name,
            0,
            yamlContent,
            existing.artifactStep0?.status ?: ArtifactStatus.PENDING_FOR_APPROVAL
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
    fun publishStep0Artifact(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineName")

        val artifactStep0 = pipeline.artifactStep0
            ?: throw IllegalStateException("Step 0 artifact not found for pipeline: $pipelineName")

        if (artifactStep0.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Step 0 artifact is not APPROVED. Current status: ${artifactStep0.status}")
        }

        val rootTopic = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Root topic not found in catalog: ${pipeline.topicKey}")

        val topicsByParent = artifactStep0.topics.groupBy { it.parentTopicKey }

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

        publishRecursive(pipeline.topicKey, rootTopic.path)
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
                    "TOPICS_GENERATION" -> artifactStep0?.status
                    "QUESTIONS_GENERATION" -> artifactStep1?.status
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