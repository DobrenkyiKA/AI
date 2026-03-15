package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import java.time.Instant

abstract class AbstractPipelineStepService(
    protected val pipelineRepository: PipelineRepository,
    protected val artifactStorage: ArtifactStorage
) : PipelineStepService {

    protected val yamlMapper: ObjectMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        // Default implementation only updates status if specialized logic is not provided
        val artifact = step.artifact ?: throw IllegalStateException("Artifact not found for step: ${step.id}")
        artifact.status = status
    }

    protected fun clearOldArtifact(pipeline: PipelineEntity, step: PipelineStepEntity) {
        if (step.artifact != null) {
            step.artifact = null
            pipelineRepository.saveAndFlush(pipeline)
        }
    }

    protected fun updatePipeline(pipeline: PipelineEntity, status: PipelineStatus) {
        pipeline.status = status
        pipeline.updatedAt = Instant.now()
        pipelineRepository.save(pipeline)
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
