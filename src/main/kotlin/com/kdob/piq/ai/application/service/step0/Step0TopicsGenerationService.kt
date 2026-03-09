package com.kdob.piq.ai.application.service.step0

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.Step0Topic
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
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
) {
    private val yamlMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    @Transactional
    fun generate(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw IllegalArgumentException("Pipeline not found: $pipelineName")

        val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Main topic not found: ${pipeline.topicKey}")

        val prompt = buildPrompt(topicDetail.name, topicDetail.coverageArea, topicDetail.exclusions, pipeline.topicKey)
        val rawOutput = generator.executePrompt(prompt)
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
        artifactStorage.saveArtifact(pipelineName, 0, yamlContent.trim())
    }

    private fun buildPrompt(topicName: String, coverageArea: String, exclusions: String, topicKey: String): String = """
        You are a senior technical interviewer.
        
        Build the most comprehensive subtopics graph/tree with structure as wide and deep as possible for the following topic:
        
        Topic: $topicName (Key: $topicKey)
        Coverage Area: $coverageArea
        Exclusions (DO NOT INCLUDE): $exclusions
        
        Rules:
        - Build all subtopics based on the topic and its coverage area.
        - Avoid topics listed in exclusions.
        - Result should be a comprehensive list of subtopics.
        - Each subtopic must have a unique key, a name, a coverage area, and a parentTopicKey.
        - For top-level subtopics, parentTopicKey should be $topicKey.
        - Output YAML ONLY in the following format:
        
        topics:
          - key: <subtopic key>
            name: <subtopic name>
            parentTopicKey: <parent key or null>
            coverageArea: <brief description of what this subtopic covers>
    """.trimIndent()

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
                constraints = null
            )
        }
    }
}
