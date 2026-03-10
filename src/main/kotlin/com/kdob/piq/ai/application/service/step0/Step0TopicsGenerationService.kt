package com.kdob.piq.ai.application.service.step0

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.application.service.GenerationStep
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.Step0Topic
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class Step0TopicsGenerationService(
    private val generator: GeminiChat,
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient
) : GenerationStep {
    private val yamlMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    override fun getStepType(): String = "TOPICS_GENERATION"

    @Transactional
    override fun generate(pipeline: PipelineEntity, step: PipelineStepEntity) {
        val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Main topic not found: ${pipeline.topicKey}")

        val systemPrompt = interpolate(step.systemPrompt, pipeline, topicDetail.name, topicDetail.coverageArea, topicDetail.exclusions)
        val userPrompt = interpolate(step.userPrompt, pipeline, topicDetail.name, topicDetail.coverageArea, topicDetail.exclusions)

        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val subTopics = parseSubTopics(rawOutput)

        // Ensure top level generated topics point to parent topic
        val topicsWithParent = subTopics.map {
            if (it.parentTopicKey == null) it.copy(parentTopicKey = pipeline.topicKey) else it
        }

        if (pipeline.artifactStep0 != null) {
            pipeline.artifactStep0 = null
            pipelineRepository.saveAndFlush(pipeline)
        }

        val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
        artifactStep0.status = ArtifactStatus.PENDING_FOR_APPROVAL
        artifactStep0.topics.addAll(topicsWithParent.map { it.toEntity(artifactStep0) })

        pipeline.artifactStep0 = artifactStep0
        pipeline.status = PipelineStatus.DRAFT
        pipeline.updatedAt = Instant.now()
        pipelineRepository.save(pipeline)

        val yamlContent = yamlMapper.writeValueAsString(
            mapOf("topics" to topicsWithParent)
        )
        artifactStorage.saveArtifact(pipeline.name, 0, yamlContent.trim())
    }

    private fun interpolate(prompt: String, pipeline: PipelineEntity, topicName: String, coverageArea: String, exclusions: String): String {
        return prompt
            .replace("{{topicName}}", topicName)
            .replace("{{topicKey}}", pipeline.topicKey)
            .replace("{{coverageArea}}", coverageArea)
            .replace("{{exclusions}}", exclusions)
    }

    @Transactional
    fun generate(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw IllegalArgumentException("Pipeline not found: $pipelineName")

        val step = pipeline.steps.find { it.stepType == getStepType() }
            ?: throw IllegalStateException("Step ${getStepType()} not found in pipeline $pipelineName")

        generate(pipeline, step)
    }

    private fun parseSubTopics(rawOutput: String): List<Step0Topic> {
        val cleaned = rawOutput.trim().removeSurrounding("```yaml", "```").trim()
        val data = yamlMapper.readValue(cleaned, Map::class.java)

        @Suppress("UNCHECKED_CAST")
        val topicsList = data["topics"] as? List<Map<String, Any>> ?: emptyList()
        
        return topicsList.map { 
            Step0Topic(
                key = it["key"] as String,
                name = it["name"] as String,
                parentTopicKey = it["parentTopicKey"] as? String,
                coverageArea = it["coverageArea"] as String,
            )
        }
    }
}