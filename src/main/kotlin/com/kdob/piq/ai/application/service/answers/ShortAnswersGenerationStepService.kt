package com.kdob.piq.ai.application.service.answers

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShortAnswersGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(ShortAnswersGenerationStepService::class.java)

    override fun getStepType(): String = "SHORT_ANSWERS_GENERATION"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline

        val answersStep = pipeline.steps.find { it.stepType == "LONG_ANSWERS_GENERATION" }
            ?: throw IllegalStateException("LONG_ANSWERS_GENERATION step not found for pipeline: ${pipeline.name}")
        val answersArtifact = answersStep.artifact as? AnswersArtifactEntity
            ?: throw IllegalStateException("Answers artifact not found for pipeline: ${pipeline.name}")

        if (answersArtifact.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Answers artifact is not APPROVED. Current status: ${answersArtifact.status}")
        }

        clearOldArtifact(pipeline, step)

        val shortAnswersArtifact = AnswersArtifactEntity(pipeline = pipeline)

        for (topicQA in answersArtifact.topicsWithQA) {
            val newTopicQA = TopicQAEntity(
                key = topicQA.key,
                name = topicQA.name,
                answersArtifact = shortAnswersArtifact
            )

            for (entry in topicQA.entries) {
                val systemPrompt = interpolateShortAnswerPrompt(
                    step.systemPrompt?.content ?: "", topicQA, entry
                )
                val userPrompt = interpolateShortAnswerPrompt(
                    step.userPrompt?.content ?: "", topicQA, entry
                )

                logger.info("Generating short answer for: {} [{}]", entry.questionText.take(80), entry.level)
                val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
                val shortAnswer = parseShortAnswer(rawOutput)

                val newEntry = QAEntryEntity(
                    questionText = entry.questionText,
                    level = entry.level,
                    answer = entry.answer,
                    shortAnswer = shortAnswer,
                    topicQA = newTopicQA
                )
                newTopicQA.entries.add(newEntry)
            }

            shortAnswersArtifact.topicsWithQA.add(newTopicQA)
        }

        step.artifact = shortAnswersArtifact
        updatePipeline(pipeline, PipelineStatus.SHORT_ANSWERS_GENERATED)

        val totalEntries = shortAnswersArtifact.topicsWithQA.sumOf { it.entries.size }
        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "totalEntries" to totalEntries,
                "topics" to shortAnswersArtifact.topicsWithQA.map { topicQA ->
                    mapOf(
                        "key" to topicQA.key,
                        "name" to topicQA.name,
                        "questions" to topicQA.entries.map { entry ->
                            mapOf(
                                "text" to entry.questionText,
                                "level" to entry.level,
                                "answer" to entry.answer,
                                "shortAnswer" to entry.shortAnswer
                            )
                        }
                    )
                }
            )
        )
        artifactStorage.saveShortAnswersArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())

        logger.info(
            "Short answers generated for pipeline '{}': {} entries across {} topics",
            pipeline.name, totalEntries, shortAnswersArtifact.topicsWithQA.size
        )
    }

    private fun interpolateShortAnswerPrompt(prompt: String, topicQA: TopicQAEntity, entry: QAEntryEntity): String {
        return prompt
            .replace("{{topicName}}", topicQA.name)
            .replace("{{level}}", entry.level)
            .replace("{{questionText}}", entry.questionText)
            .replace("{{answer}}", entry.answer ?: "")
    }

    private fun parseShortAnswer(rawOutput: String): String {
        return try {
            val data = parseYaml(rawOutput)
            data["shortAnswer"] as? String ?: rawOutput.trim()
        } catch (e: Exception) {
            rawOutput.trim()
        }
    }
}
