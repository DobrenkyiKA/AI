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

    private fun buildPrompt(topicName: String, coverageArea: String, exclusions: String, topicKey: String): String = buildString {
        appendLine("You are a senior technical interviewer and subject matter expert.")
        appendLine("You need to build the most comprehensive library of possible questions for developers of all levels. ")
        appendLine("First, your task is to build a highly granular and comprehensive taxonomy of subtopics which the questions can then address for a technical interview.")
        appendLine("The goal is to create a tree structure that is as deep and wide as possible, covering every nuance of the specified topic.")
        appendLine()
        appendLine("Topic: $topicName (Key: $topicKey)")
        appendLine("Coverage Area: $coverageArea")
        if (exclusions.isNotBlank()) {
            appendLine("Exclusions (DO NOT INCLUDE): $exclusions")
        }
        appendLine()
        appendLine("Rules for Subtopic Generation:")
        appendLine("- Breakdown: Decompose the main topic into multiple levels of subtopics (at least 3-4 levels deep where appropriate).")
        appendLine("- Granularity: Do not stop at high-level categories. Break them down into specific concepts, internal workings, edge cases, and advanced usage.")
        appendLine("- Completeness: Ensure every aspect mentioned in the Coverage Area is thoroughly expanded.")
        if (exclusions.isNotBlank()) {
            appendLine("- Strict Exclusions: Do not include ANY topics or subtopics that fall under the exclusions list.")
        }
        appendLine("- Structure: Each subtopic must have a unique 'key', a 'name', a 'coverageArea' (detailed description), and a 'parentTopicKey'.")
        appendLine("- Quoting: ALWAYS wrap 'name' and 'coverageArea' values in double quotes to ensure valid YAML (e.g., name: \"Topic Name\").")
        appendLine("- Hierarchy: For top-level subtopics, 'parentTopicKey' must be '$topicKey'. Subsequent levels should point to their respective parent subtopic keys.")
        appendLine("- Unique Keys: Use descriptive, lowercase, kebab-case keys (e.g., 'java-collections-list-internal').")
        appendLine("- Volume: Aim for a large number of subtopics (typically 150+) to ensure full coverage.")
        appendLine("- Output Format: YAML ONLY in the specified format.")
        appendLine()
        appendLine("topics:")
        appendLine("  - key: \"<subtopic-key>\"")
        appendLine("    name: \"<subtopic-name>\"")
        appendLine("    parentTopicKey: \"<parent-key>\"")
        appendLine("    coverageArea: \"<detailed description of what this specific subtopic covers, including key concepts to be tested>\"")
    }.trim()

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

//
//
//  Structure: Each subtopic must have a unique 'key', a 'name', a 'coverageArea' (detailed description), and a 'parentTopicKey'.
//- Quoting: ALWAYS wrap 'name' and 'coverageArea' values in double quotes to ensure valid YAML (e.g., name: \"Topic Name\").
//- Hierarchy: For top-level subtopics, 'parentTopicKey' must be '$topicKey'. Subsequent levels should point to their respective parent subtopic keys.
//- Unique Keys: Use descriptive, lowercase, kebab-case keys (e.g., 'java-collections-list-internal').
//- Volume: Aim for a large number of subtopics (typically 150+) to ensure full coverage.
//- Output Format: YAML ONLY in the specified format.
//
//topics:
//  - key: "<subtopic-key>"
//    name: "<subtopic-name>"
//    parentTopicKey: "<parent-key>"
//    coverageArea: "<detailed description of what this specific subtopic covers, including key concepts to be tested>"