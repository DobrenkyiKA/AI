package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.step1.GeminiQuestionGenerator
import com.kdob.piq.ai.application.service.step1.GeneratedQuestionValidator
import com.kdob.piq.ai.domain.model.GeneratedQuestion
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogHttpClient
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service


@Service
class Step1QuestionGenerationService(
    private val generator: GeminiQuestionGenerator,
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
    private val questionCatalogHttpClient: QuestionCatalogHttpClient
) {
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun generate(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw IllegalArgumentException("Pipeline not found: $pipelineName")

        val artifactStep0 = pipeline.artifactStep0 ?: throw IllegalStateException("Step 0 artifact not found for pipeline: $pipelineName")
        val topics = artifactStep0.topics
        val topicKeys = topics.map { it.key }.toSet()
        val expectedCount = 5//pipeline.questionCount

        val existingPrompts =
            questionCatalogHttpClient.findQuestionPrompts(topicKeys).map { it.prompt.trim().lowercase() }.toSet()

        val prompt = buildPrompt(topics)

        val rawOutput = generator.generateQuestions(prompt)

        val parsed = yamlMapper.readValue(rawOutput, Map::class.java) as Map<String, List<Map<String, String>>>

        val questions = parsed["questions"]!!.mapIndexed { index, q ->
            GeneratedQuestion(
                id = "q-${index + 1}", topicKey = q["topicKey"]!!, prompt = q["prompt"]!!
            )
        }

        artifactStorage.saveStep1Questions(
            pipelineName, yamlMapper.writeValueAsString(mapOf("questions" to questions))
        )

        pipelineRepository.updateStatus(
            pipelineName, PipelineStatus.PENDING_FOR_ARTIFACT_APPROVAL
        )
    }

    private fun buildPrompt(topics: MutableSet<TopicEntity>): String = """
You are a senior technical interviewer.

Generate exactly questionCount interview-grade questions for each of the following topics.

Topics:
${topics.joinToString("\n") { "- ${it.title}: ${it.description}" }}

Rules:
- Each question must belong to exactly one topic
- No answers
- No explanations
- Output YAML ONLY in the following format:

questions:
  - topicKey: <topic key>
    prompt: <question text>
""".trimIndent()
}
//Do NOT repeat or paraphrase the following questions:
//${existingQuestions.joinToString("\n") { "- $it" }}