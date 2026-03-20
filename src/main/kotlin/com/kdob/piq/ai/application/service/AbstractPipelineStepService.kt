package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.GenerationLogEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

abstract class AbstractPipelineStepService(
    protected val pipelineService: PipelineService,
    protected val artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : PipelineStepService {
    protected val transactionTemplate = TransactionTemplate(transactionManager)
    protected val yamlMapper: ObjectMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    protected fun initializeArtifact(pipelineName: String, step: PipelineStepEntity) {
        val artifact = transactionTemplate.execute {
            val pipelineEntity = pipelineService.get(pipelineName)
            val pipelineStepEntity: PipelineStepEntity = pipelineEntity.steps.find { it.id == step.id }!!
            val artifact = pipelineStepEntity.artifact
            artifact?.status = ArtifactStatus.GENERATION_IN_PROGRESS
            artifact
        }

        if (artifact == null) {
            log(pipelineName, step.stepOrder, "Starting new ${step.stepType} Generation...")
            initializeArtifactInternal(pipelineName, step.id!!)
        } else {
            log(pipelineName, step.stepOrder, "Resuming ${step.stepType} Generation...")
        }
    }

    protected abstract fun initializeArtifactInternal(pipelineName: String, stepId: Long)

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        // Default implementation only updates status if specialized logic is not provided
        val artifact = step.artifact ?: throw IllegalStateException("Artifact not found for step: ${step.id}")
        artifact.status = status
    }

    protected fun finalizeArtifact(pipelineName: String, stepId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineService.get(pipelineName)
            val step = pipeline.steps.find { it.id == stepId }!!
            step.artifact?.status = ArtifactStatus.PENDING_FOR_APPROVAL
            updatePipeline(pipeline)
        }
    }

    private fun updatePipeline(pipeline: PipelineEntity) {
        pipeline.status = PipelineStatus.WAITING_ARTIFACT_APPROVAL
        pipeline.updatedAt = Instant.now()
        pipelineService.save(pipeline)
    }

    protected fun isPipelineStopped(pipelineName: String, stepOrder: Int): Boolean {
        val pipeline = pipelineService.get(pipelineName)
        return when (pipeline.status) {
            PipelineStatus.PAUSED -> {
                log(pipelineName, stepOrder, "Generation PAUSED by user.")
                true
            }
            PipelineStatus.ABORTED -> {
                log(pipelineName, stepOrder, "Generation ABORTED by user.")
                true
            }
            else -> false
        }
    }

    protected fun getStepPrompts(pipelineName: String, stepId: Long): Pair<String, String> {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineService.get(pipelineName)
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            Pair(step.systemPrompt?.content ?: "", step.userPrompt?.content ?: "")
        }
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

    protected fun log(pipelineName: String, stepOrder: Int, message: String) {
        getLogger().info("Pipeline: [$pipelineName], Step: [$stepOrder], Message: [$message]")
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineService.get(pipelineName)
            generationLogRepository.save(GenerationLogEntity(pipeline, message, stepOrder))
        }
    }
}
