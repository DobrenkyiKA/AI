package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.GeminiQuestionGenerator
import com.kdob.piq.ai.client.quesiton.QuestionCatalogHttpClient
import com.kdob.piq.ai.domain.GeneratedQuestion
import com.kdob.piq.ai.domain.PipelineStatus
import com.kdob.piq.ai.domain.TopicDefinition
import com.kdob.piq.ai.domain.TopicMeta
import com.kdob.piq.ai.persistence.PipelineRepository
import com.kdob.piq.ai.storage.ArtifactStorage
import com.kdob.piq.ai.application.validation.GeneratedQuestionValidator
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml
import java.util.*
import kotlin.collections.joinToString


@Service
class Step1QuestionGenerationService(
    private val generator: GeminiQuestionGenerator,
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
    private val questionCatalogHttpClient: QuestionCatalogHttpClient
) {

    fun generate(
        pipelineId: UUID
    ) {
        val step0Yaml = artifactStorage.loadStep0Artifact(pipelineId)
        val yaml = Yaml()
        val topicDefinition =
            yaml.loadAs(step0Yaml, TopicDefinition::class.java)

        val topicKeys = topicDefinition.topics.map { it.key }.toSet()
        val expectedCount = topicDefinition.generation.questionCount

        // ✅ Fetch existing questions from Question microservice
        val existingPrompts = questionCatalogHttpClient
            .findQuestionPrompts(topicKeys)
            .map { it.prompt.trim().lowercase() }
            .toSet()

        val prompt = buildPrompt(
            topics = topicDefinition.topics,
            existingQuestions = existingPrompts,
            questionCount = expectedCount
        )

        val rawOutput = generator.generateQuestions(prompt)

        val parsed = yaml.load<Map<String, List<Map<String, String>>>>(rawOutput)

        val questions = parsed["questions"]!!.mapIndexed { index, q ->
            GeneratedQuestion(
                id = "q-${index + 1}",
                topicKey = q["topicKey"]!!,
                prompt = q["prompt"]!!
            )
        }

        GeneratedQuestionValidator.validate(
            generated = questions,
            expectedCount = expectedCount,
            existingPrompts = existingPrompts,
            validTopicKeys = topicKeys
        )

        artifactStorage.saveStep1Questions(
            pipelineId,
            yaml.dump(mapOf("questions" to questions))
        )

        pipelineRepository.updateStatus(
            pipelineId,
            PipelineStatus.WAITING_FOR_APPROVAL
        )
    }

    private fun buildPrompt(
        topics: List<TopicMeta>,
        existingQuestions: Set<String>,
        questionCount: Int
    ): String =
        """
You are a senior technical interviewer.

Generate exactly $questionCount interview-grade questions.

Topics:
${topics.joinToString("\n") { "- ${it.key}: ${it.description}" }}

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