package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.PipelineTopic
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsPipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toPipelineTopicEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

abstract class AbstractPipelineStepService(
    protected val pipelineRepository: PipelineRepository,
    protected val artifactStorage: ArtifactStorage
) : PipelineStepService {

    protected val yamlMapper: ObjectMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

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

    protected fun saveTopicsArtifact(pipeline: PipelineEntity, step: PipelineStepEntity, topics: List<PipelineTopic>) {
        clearOldArtifact(pipeline, step)

        val topicsArtifact = TopicsPipelineArtifactEntity(pipeline = pipeline)
        topicsArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        topicsArtifact.topics.addAll(topics.map { it.toPipelineTopicEntity(topicsArtifact) })

        step.artifact = topicsArtifact
        updatePipeline(pipeline, PipelineStatus.DRAFT)

        val yamlContent = yamlMapper.writeValueAsString(mapOf("topics" to topics))
        artifactStorage.saveTopicsArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())
    }

    protected fun parseYaml(rawOutput: String): Map<*, *> {
        val cleaned = rawOutput.trim().removeSurrounding("```yaml", "```").trim()
        return try {
            yamlMapper.readValue(cleaned, Map::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse YAML output: ${e.message}", e)
        }
    }

    protected fun parseSubTopics(rawOutput: String): List<PipelineTopic> {
        val data = parseYaml(rawOutput)
        @Suppress("UNCHECKED_CAST")
        val topicsList = data["topics"] as? List<Map<String, Any>> ?: emptyList()

        return topicsList.map {
            PipelineTopic(
                key = it["key"] as String,
                name = it["name"] as String,
                parentTopicKey = it["parentTopicKey"] as? String,
                coverageArea = it["coverageArea"] as String,
            )
        }
    }

    protected fun interpolateCommon(prompt: String, pipeline: PipelineEntity, topicName: String, coverageArea: String): String {
        return prompt
            .replace("{{topicName}}", topicName)
            .replace("{{topicKey}}", pipeline.topicKey)
            .replace("{{coverageArea}}", coverageArea)
    }

    protected fun handleExclusions(prompt: String, exclusions: String): String {
        return if (exclusions.isNotBlank()) {
            prompt.replace("{{exclusions}}", exclusions)
        } else {
            prompt.lines()
                .filter { !it.contains("{{exclusions}}") && !it.contains("Strict Exclusions") }
                .joinToString("\n")
        }
    }
}
