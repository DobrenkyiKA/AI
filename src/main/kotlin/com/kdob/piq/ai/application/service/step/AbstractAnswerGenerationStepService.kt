package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.persistence.entity.artifact.answer.AnswersArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.artifact.answer.QAEntryEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.artifact.answer.TopicQAEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.transaction.PlatformTransactionManager

abstract class AbstractAnswerGenerationStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService
) : AbstractQAStepService(
    pipelineService, artifactStorage, pipelineStatusService,
    transactionManager, loggerService, generator, pipelineArtifactStatusService
) {

    protected abstract fun inputStepType(): StepType

    protected abstract fun answerLabel(): String

    protected abstract fun createEntry(sourceEntry: QAEntryEntity, generatedAnswer: String, topicQA: TopicQAEntity): QAEntryEntity

    protected abstract fun parseGeneratedAnswer(rawOutput: String): String

    protected abstract fun interpolatePrompt(prompt: String, topicQA: TopicQAEntity, entry: QAEntryEntity): String

    override fun findNext(step: PipelineStepEntity): TopicQAEntity? {
        loggerService.log(step, "Finding next topic to generate ${answerLabel()} for...")
        return transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as AnswersArtifactEntity
            val inputStep = pipeline.steps.find { it.stepType == inputStepType() }!!
            val inputArtifact = inputStep.artifact as AnswersArtifactEntity

            val generatedTopicMap = artifact.topicsWithQA.associateBy { it.key }

            inputArtifact.topicsWithQA.find { inputTopic ->
                val generatedTopic = generatedTopicMap[inputTopic.key]
                generatedTopic == null || generatedTopic.entries.size < inputTopic.entries.size
            }?.also { it.entries.size }
        }?.also {
            loggerService.log(step, "Found next topic: ${it.name}")
        }
    }

    override fun processItem(step: PipelineStepEntity, item: Any) {
        val inputTopicQA = item as TopicQAEntity
        val missingEntries = transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as AnswersArtifactEntity
            val generatedTopic = artifact.topicsWithQA.find { it.key == inputTopicQA.key }
            val existingTexts = generatedTopic?.entries?.map { it.questionText }?.toSet() ?: emptySet()
            inputTopicQA.entries.filter { it.questionText !in existingTexts }
        }

        if (missingEntries.isEmpty()) return

        loggerService.log(step, "Generating ${answerLabel()} for topic: [${inputTopicQA.name}]. [${missingEntries.size}] questions remaining.")

        for (entry in missingEntries) {
            if (pipelineStatusService.isStopped(step)) return

            val systemPrompt = interpolatePrompt(step.systemPrompt?.content ?: "", inputTopicQA, entry)
            val userPrompt = interpolatePrompt(step.userPrompt?.content ?: "", inputTopicQA, entry)

            loggerService.log(step, "Generating ${answerLabel()} for: [${entry.questionText.take(80)}] [${entry.level}]")
            loggerService.log(step, "Calling AI service...")
            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            loggerService.log(step, "AI service response received.")
            val answer = parseGeneratedAnswer(rawOutput)

            val yamlContent = transactionTemplate.execute {
                val pipeline = pipelineService.get(step.pipeline.name)
                val currentStep = pipeline.steps.find { it.id == step.id }!!
                val artifact = currentStep.artifact as AnswersArtifactEntity

                var topicQA = artifact.topicsWithQA.find { it.key == inputTopicQA.key }
                if (topicQA == null) {
                    topicQA = TopicQAEntity(
                        key = inputTopicQA.key,
                        name = inputTopicQA.name,
                        answersArtifact = artifact
                    ).apply { this.parentChain = inputTopicQA.parentChain }
                    artifact.topicsWithQA.add(topicQA)
                }

                topicQA.entries.add(createEntry(entry, answer, topicQA))
                
                loggerService.log(currentStep, "Saving pipeline state to database...")
                pipelineService.saveAndFlush(pipeline)
                loggerService.log(currentStep, "Pipeline state saved.")
                prepareIncrementalYaml(artifact)
            }

            saveArtifactYaml(step, yamlContent)
            loggerService.log(step, "Saved ${answerLabel()} for topic: [${inputTopicQA.name}], question: [${entry.questionText.take(50)}]")
        }
    }
}
