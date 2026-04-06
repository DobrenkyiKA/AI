package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.infrastructure.persistence.entity.QAEntryEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicQAEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

@Service
class ShortAnswersGenerationPipelineStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService
) : AbstractAnswerGenerationStepService(
    pipelineService, artifactStorage, pipelineStatusService,
    transactionManager, loggerService, generator, pipelineArtifactStatusService
) {

    override fun getStepType(): StepType = StepType.SHORT_ANSWERS_GENERATION

    override fun previousStepType(): StepType = StepType.LONG_ANSWERS_GENERATION

    override fun inputStepType(): StepType = StepType.LONG_ANSWERS_GENERATION

    override fun answerLabel(): String = "short answer"

    override fun totalCountLabel(): String = "totalEntries"

    override fun entryToYamlMap(entry: QAEntryEntity): Map<String, Any?> = mapOf(
        "text" to entry.questionText,
        "level" to entry.level,
        "answer" to entry.answer,
        "shortAnswer" to entry.shortAnswer
    )

    override fun yamlToEntry(q: Map<String, Any>, topicQA: TopicQAEntity): QAEntryEntity = QAEntryEntity(
        questionText = q["text"] as String,
        level = q["level"] as String,
        answer = q["answer"] as? String,
        shortAnswer = q["shortAnswer"] as? String,
        topicQA = topicQA
    )

    override fun createEntry(sourceEntry: QAEntryEntity, generatedAnswer: String, topicQA: TopicQAEntity): QAEntryEntity =
        QAEntryEntity(
            questionText = sourceEntry.questionText,
            level = sourceEntry.level,
            answer = sourceEntry.answer,
            shortAnswer = generatedAnswer,
            topicQA = topicQA
        )

    override fun parseGeneratedAnswer(rawOutput: String): String {
        return try {
            val data = parseYaml(rawOutput)
            data["shortAnswer"] as? String ?: rawOutput.trim()
        } catch (e: Exception) {
            rawOutput.trim()
        }
    }

    override fun interpolatePrompt(prompt: String, topicQA: TopicQAEntity, entry: QAEntryEntity): String {
        return prompt
            .replace("{{topicName}}", topicQA.name)
            .replace("{{parentChain}}", topicQA.parentChain ?: "")
            .replace("{{coverageArea}}", "")
            .replace("{{level}}", entry.level)
            .replace("{{questionText}}", entry.questionText)
            .replace("{{answer}}", entry.answer ?: "")
    }
}
