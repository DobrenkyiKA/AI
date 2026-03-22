package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

abstract class AbstractPipelineStepService(
    protected val pipelineService: PipelineService,
    protected val artifactStorage: ArtifactStorage,
    protected val pipelineStatusService: PipelineStatusService,
    protected val transactionManager: PlatformTransactionManager,
    protected val loggerService: LoggerService,
    protected val generator: GoogleAiChatService,
    protected val pipelineArtifactStatusService: PipelineArtifactStatusService
) : PipelineStepService {
    protected val transactionTemplate = TransactionTemplate(transactionManager)
    protected val yamlMapper: ObjectMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    protected fun initializeArtifact(pipelineStep: PipelineStepEntity) {
        pipelineArtifactStatusService.toInProgress(pipelineStep)

        if (pipelineStep.artifact == null) {
            loggerService.log(pipelineStep, "Starting new [${pipelineStep.stepType}] Generation...")
            initializeArtifactInternal(pipelineStep)
        } else {
            loggerService.log(pipelineStep, "Resuming [${pipelineStep.stepType}] Generation...")
        }
    }

    protected abstract fun initializeArtifactInternal(pipelineStep: PipelineStepEntity)

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        val artifact = step.artifact ?: throw IllegalStateException("Artifact not found for step: ${step.id}")
        artifact.status = status
    }

    protected fun finalizeArtifact(pipelineStep: PipelineStepEntity) {
        pipelineArtifactStatusService.toPendingApproval(pipelineStep)
        pipelineStatusService.toWaitingApproval(pipelineStep.pipeline)
    }

    protected fun parseYaml(rawOutput: String): Map<*, *> {
        val cleaned = rawOutput.trim().removeSurrounding("```yaml", "```").trim()
        return try {
            yamlMapper.readValue(cleaned, Map::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse YAML output: ${e.message}", e)
        }
    }
}
