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
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
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

    fun generate(
        pipelineName: String
    ) {
        val step0Yaml = artifactStorage.loadStep0Artifact(pipelineName)
        val pipeline = yamlMapper.readValue(step0Yaml, PipelineEntity::class.java)

        val topicKeys = pipeline.topics.map { it.key }.toSet()
        val expectedCount = 5//pipeline.questionCount

        // ✅ Fetch existing questions from Question microservice
        val existingPrompts =
            questionCatalogHttpClient.findQuestionPrompts(topicKeys).map { it.prompt.trim().lowercase() }.toSet()

        val prompt = buildPrompt(
            topics = pipeline.topics, existingQuestions = existingPrompts
        )

        val rawOutput = generator.generateQuestions(prompt)

        val parsed = yamlMapper.readValue(rawOutput, Map::class.java) as Map<String, List<Map<String, String>>>

        val questions = parsed["questions"]!!.mapIndexed { index, q ->
            GeneratedQuestion(
                id = "q-${index + 1}", topicKey = q["topicKey"]!!, prompt = q["prompt"]!!
            )
        }

        GeneratedQuestionValidator.validate(
            generated = questions,
            expectedCount = expectedCount,
            existingPrompts = existingPrompts,
            validTopicKeys = topicKeys
        )

        artifactStorage.saveStep1Questions(
            pipelineName, yamlMapper.writeValueAsString(mapOf("questions" to questions))
        )

        pipelineRepository.updateStatus(
            pipelineName, PipelineStatus.WAITING_FOR_APPROVAL
        )
    }

    private fun buildPrompt(
        topics: MutableSet<TopicEntity>, existingQuestions: Set<String>
    ): String = """
You are a senior technical interviewer.

Generate exactly questionCount interview-grade questions.

Topics:
${topics.joinToString("\n") { "- {it.key}: {it.description}" }}

Do NOT repeat or paraphrase the following questions:
${existingQuestions.joinToString("\n") { "- $it" }}

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
