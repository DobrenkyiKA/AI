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
class LongAnswersGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(LongAnswersGenerationStepService::class.java)

    override fun getStepType(): String = "LONG_ANSWERS_GENERATION"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline

        val questionsStep = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }
            ?: throw IllegalStateException("QUESTIONS_GENERATION step not found for pipeline: ${pipeline.name}")
        val questionsArtifact = questionsStep.artifact as? AnswersArtifactEntity
            ?: throw IllegalStateException("Questions artifact not found for pipeline: ${pipeline.name}")

        check(questionsArtifact.status == ArtifactStatus.APPROVED) {
            "Questions artifact is not APPROVED. Current status: ${questionsArtifact.status}"
        }

        clearOldArtifact(pipeline, step)

        val answersArtifact = AnswersArtifactEntity(pipeline = pipeline)

        for (topicQA in questionsArtifact.topicsWithQA) {
            val newTopicQA = TopicQAEntity(
                key = topicQA.key,
                name = topicQA.name,
                answersArtifact = answersArtifact
            )

            for (entry in topicQA.entries) {
                val systemPrompt = interpolateAnswerPrompt(
                    step.systemPrompt?.content ?: "", topicQA, entry
                )
                val userPrompt = interpolateAnswerPrompt(
                    step.userPrompt?.content ?: "", topicQA, entry
                )

                logger.info("Generating answer for: {} [{}]", entry.questionText.take(80), entry.level)
                val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
                val answer = parseAnswer(rawOutput)

                val newEntry = QAEntryEntity(
                    questionText = entry.questionText,
                    level = entry.level,
                    answer = answer,
                    topicQA = newTopicQA
                )
                newTopicQA.entries.add(newEntry)
            }

            answersArtifact.topicsWithQA.add(newTopicQA)
        }

        step.artifact = answersArtifact
        answersArtifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        updatePipeline(pipeline, PipelineStatus.WAITING_ARTIFACT_APPROVAL)

        val totalAnswers = answersArtifact.topicsWithQA.sumOf { it.entries.size }
        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "totalAnswers" to totalAnswers,
                "topics" to answersArtifact.topicsWithQA.map { topicQA ->
                    mapOf(
                        "key" to topicQA.key,
                        "name" to topicQA.name,
                        "questions" to topicQA.entries.map { entry ->
                            mapOf(
                                "text" to entry.questionText,
                                "level" to entry.level,
                                "answer" to entry.answer
                            )
                        }
                    )
                }
            )
        )
        artifactStorage.saveAnswersArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())

        logger.info(
            "Long answers generated for pipeline '{}': {} answers across {} topics",
            pipeline.name, totalAnswers, answersArtifact.topicsWithQA.size
        )
    }

    private fun interpolateAnswerPrompt(prompt: String, topicQA: TopicQAEntity, entry: QAEntryEntity): String {
        return prompt
            .replace("{{topicName}}", topicQA.name)
            .replace("{{coverageArea}}", "")
            .replace("{{level}}", entry.level)
            .replace("{{questionText}}", entry.questionText)
    }

    private fun parseAnswer(rawOutput: String): String {
        return try {
            val data = parseYaml(rawOutput)
            data["answer"] as? String ?: rawOutput.trim()
        } catch (e: Exception) {
            rawOutput.trim()
        }
    }
}
