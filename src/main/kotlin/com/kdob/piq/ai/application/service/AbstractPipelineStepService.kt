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

    override fun generate(pipelineStep: PipelineStepEntity) {
        initializeArtifact(pipelineStep)
        while (pipelineStatusService.isNotStopped(pipelineStep)) {
            val next = findNext(pipelineStep) ?: run {
                loggerService.log(pipelineStep, "${getLabel()} completed successfully.")
                finalizeArtifact(pipelineStep)
                return
            }
            try {
                processItem(pipelineStep, next)
            } catch (e: Exception) {
                loggerService.log(pipelineStep, "Error during ${getLabel()}: ${e.message}")
                throw e
            }
        }
    }

    protected abstract fun findNext(step: PipelineStepEntity): Any?

    protected abstract fun processItem(step: PipelineStepEntity, item: Any)

    protected abstract fun initializeArtifactInternal(pipelineStep: PipelineStepEntity)

//    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
//        val artifact = step.artifact ?: throw IllegalStateException("Artifact not found for step: ${step.id}")
//        artifact.status = status
//    }

    protected fun initializeArtifact(pipelineStep: PipelineStepEntity) {
        pipelineArtifactStatusService.toInProgress(pipelineStep)

        if (pipelineStep.artifact == null) {
            loggerService.log(pipelineStep, "Initializing new artifact generation for [${pipelineStep.stepType.name}].")
            initializeArtifactInternal(pipelineStep)
        } else {
            loggerService.log(pipelineStep, "Resuming [${pipelineStep.stepType.name}] Generation...")
        }
    }

    protected fun finalizeArtifact(pipelineStep: PipelineStepEntity) {
        pipelineArtifactStatusService.toPendingApproval(pipelineStep)
        pipelineStatusService.toWaitingApproval(pipelineStep.pipeline)
    }

    protected fun saveArtifactYaml(pipelineStep: PipelineStepEntity, yamlContent: String) {
        artifactStorage.saveArtifact(
            pipelineStep.pipeline.topicKey,
            pipelineStep.pipeline.name,
            getStepType(),
            yamlContent
        )
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
