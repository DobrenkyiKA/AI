package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.GenerationLogEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

abstract class AbstractPipelineStepService(
    protected val pipelineRepository: PipelineRepository,
    protected val artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : PipelineStepService {
    protected val transactionTemplate = TransactionTemplate(transactionManager)
    protected val yamlMapper: ObjectMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    protected fun initializeArtifact(pipelineId: Long, step: PipelineStepEntity) {
        val artifact = transactionTemplate.execute {
            val pipelineEntity = pipelineRepository.findById(pipelineId)!!
            val pipelineStepEntity: PipelineStepEntity = pipelineEntity.steps.find { it.id == step.id }!!
            pipelineStepEntity.artifact as? TopicTreeArtifactEntity
        }

        if (artifact == null) {
            log(pipelineId, step.stepOrder, "Starting new Topic Tree Generation...")
            initializeArtifactInternal(pipelineId, step.id!!)
        } else {
            log(pipelineId, step.stepOrder, "Resuming Topic Tree Generation...")
        }
    }

    protected abstract fun initializeArtifactInternal(pipelineId: Long, stepId: Long)

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        // Default implementation only updates status if specialized logic is not provided
        val artifact = step.artifact ?: throw IllegalStateException("Artifact not found for step: ${step.id}")
        artifact.status = status
    }

    protected fun finalizeArtifact(pipelineId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            updatePipeline(pipeline)
        }
    }

    private fun updatePipeline(pipeline: PipelineEntity) {
        pipeline.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
        pipeline.updatedAt = Instant.now()
        pipelineRepository.save(pipeline)
    }

    protected fun isPipelineStopped(pipelineId: Long, stepOrder: Int): Boolean {
        val pipeline = pipelineRepository.findById(pipelineId)!!
        return when (pipeline.status) {
            PipelineStatus.PAUSED -> {
                log(pipelineId, stepOrder, "Generation PAUSED by user.")
                true
            }
            PipelineStatus.ABORTED -> {
                log(pipelineId, stepOrder, "Generation ABORTED by user.")
                true
            }
            else -> false
        }
    }

    protected fun getStepPrompts(pipelineId: Long, stepId: Long): Pair<String, String> {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            Pair(step.systemPrompt?.content ?: "", step.userPrompt?.content ?: "")
        }!!
    }

    protected fun parseYaml(rawOutput: String): Map<*, *> {
        val cleaned = rawOutput.trim().removeSurrounding("```yaml", "```").trim()
        return try {
            yamlMapper.readValue(cleaned, Map::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse YAML output: ${e.message}", e)
        }
    }

    protected fun getLogger(): Logger {
        return LoggerFactory.getLogger(this::class.java)
    }

    protected fun log(pipelineId: Long, stepOrder: Int, message: String) {
        getLogger().info("Pipeline: [$pipelineId], Step: [$stepOrder], Message: [$message]")
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            generationLogRepository.save(GenerationLogEntity(pipeline, message, stepOrder))
        }
    }
}
