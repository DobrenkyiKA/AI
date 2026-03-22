package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.AnswersArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.QAEntryEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicQAEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

private const val LONG_ANSWERS_GENERATION_STEP_TYPE = "LONG_ANSWERS_GENERATION"

@Service
class LongAnswersGenerationPipelineStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService,
    private val generationPipelineStepService: QuestionsGenerationPipelineStepService
) : AbstractPipelineStepService(
    pipelineService,
    artifactStorage,
    pipelineStatusService,
    transactionManager,
    loggerService,
    generator,
    pipelineArtifactStatusService
) {

    override fun getStepType(): String = LONG_ANSWERS_GENERATION_STEP_TYPE

    override fun generate(pipelineStep: PipelineStepEntity) {
        initializeArtifact(pipelineStep)
        while (true) {
            if (pipelineStatusService.isStopped(pipelineStep)) return

            val nextTopicQA = findNextTopicToGenerate(pipelineStep)
            if (nextTopicQA == null) {
                loggerService.log(pipelineStep, "Long Answers Generation completed successfully.")
                finalizeArtifact(pipelineStep)
                return
            }
            try {
                generateForTopic(pipelineStep, nextTopicQA)
            } catch (e: Exception) {
                loggerService.log(pipelineStep, "Error during generation for ${nextTopicQA.name}: ${e.message}")
                throw e
            }
        }
    }

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        val artifact = step.artifact as? AnswersArtifactEntity
            ?: throw IllegalStateException("Answers artifact not found")
        artifact.status = status

        val data = parseYaml(yamlContent)

        @Suppress("UNCHECKED_CAST")
        val topicsList = data["topics"] as? List<Map<String, Any>> ?: emptyList()

        val incomingByKey = topicsList.associateBy { it["key"] as String }
        artifact.topicsWithQA.removeIf { it.key !in incomingByKey }
        val existingByKey = artifact.topicsWithQA.associateBy { it.key }

        for (t in topicsList) {
            val key = t["key"] as String
            val name = t["name"] as String
            val parentChain = t["parentChain"] as? String

            @Suppress("UNCHECKED_CAST")
            val questions = t["questions"] as? List<Map<String, Any>> ?: emptyList()
            val existing = existingByKey[key]
            if (existing != null) {
                existing.parentChain = parentChain
                existing.entries.clear()
                existing.entries.addAll(questions.map { q ->
                    QAEntryEntity(
                        questionText = q["text"] as String,
                        level = q["level"] as String,
                        answer = q["answer"] as? String,
                        topicQA = existing
                    )
                })
            } else {
                val topicQA = TopicQAEntity(
                    key = key,
                    name = name,
                    answersArtifact = artifact
                ).apply {
                    this.parentChain = parentChain
                }
                topicQA.entries.addAll(questions.map { q ->
                    QAEntryEntity(
                        questionText = q["text"] as String,
                        level = q["level"] as String,
                        answer = q["answer"] as? String,
                        topicQA = topicQA
                    )
                })
                artifact.topicsWithQA.add(topicQA)
            }
        }

        artifactStorage.saveAnswersArtifact(step.pipeline.topicKey, step.pipeline.name, yamlContent.trim())
    }

    override fun initializeArtifactInternal(pipelineStep: PipelineStepEntity) {
        transactionTemplate.execute {

            val questionsStep = pipelineStep.pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }
                ?: throw IllegalStateException("QUESTIONS_GENERATION step not found for pipeline: ${pipelineStep.pipeline.name}")
            val questionsArtifact = questionsStep.artifact as? AnswersArtifactEntity
                ?: throw IllegalStateException("Questions artifact not found for pipeline: ${pipelineStep.pipeline.name}")

            check(questionsArtifact.status == ArtifactStatus.APPROVED) {
                "Questions artifact is not APPROVED. Current status: ${questionsArtifact.status}"
            }

            val artifact = AnswersArtifactEntity(pipeline = pipelineStep.pipeline)
            artifact.status = ArtifactStatus.GENERATION_IN_PROGRESS
            pipelineStep.artifact = artifact

            pipelineService.save(pipelineStep.pipeline)
            val yamlContent = prepareIncrementalYaml(artifact)
            artifactStorage.saveAnswersArtifact(pipelineStep.pipeline.topicKey, pipelineStep.pipeline.name, yamlContent)
            loggerService.log(pipelineStep, "Initialized Long Answers Artifact.")
        }
    }

    private fun findNextTopicToGenerate(pipelineStep: PipelineStepEntity): TopicQAEntity? {
        return transactionTemplate.execute {
            val pipeline = pipelineStep.pipeline
            val artifact = pipelineStep.artifact as AnswersArtifactEntity
            val questionsArtifact = generationPipelineStepService.getArtifact(pipeline)

            val generatedTopicMap = artifact.topicsWithQA.associateBy { it.key }

            questionsArtifact.topicsWithQA.find { qTopic ->
                val gTopic = generatedTopicMap[qTopic.key]
                gTopic == null || gTopic.entries.size < qTopic.entries.size
            }?.also { it.entries.size }
        }
    }

    private fun generateForTopic(pipelineStep: PipelineStepEntity, inputTopicQA: TopicQAEntity) {
        val missingEntries = transactionTemplate.execute {
            val artifact = pipelineStep.artifact as AnswersArtifactEntity
            val gTopic = artifact.topicsWithQA.find { it.key == inputTopicQA.key }
            val existingTexts = gTopic?.entries?.map { it.questionText }?.toSet() ?: emptySet()
            inputTopicQA.entries.filter { it.questionText !in existingTexts }
        }

        if (missingEntries.isEmpty()) return

        loggerService.log( pipelineStep, "Generating long answers for topic: [${inputTopicQA.name}]. [${missingEntries.size}] questions remaining.")

        for (entry in missingEntries) {
            if (pipelineStatusService.isStopped(pipelineStep)) return
            val (systemPromptTemplate, userPromptTemplate) = getStepPrompts(pipelineStep)
            val systemPrompt = interpolateAnswerPrompt(systemPromptTemplate, inputTopicQA, entry)
            val userPrompt = interpolateAnswerPrompt(userPromptTemplate, inputTopicQA, entry)

            loggerService.log(pipelineStep, "Generating answer for: [${entry.questionText.take(80)}] [${entry.level}]")
            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            val answer = parseAnswer(rawOutput)

            val yamlContent = transactionTemplate.execute {
                val artifact = pipelineStep.artifact as AnswersArtifactEntity

                var topicQA = artifact.topicsWithQA.find { it.key == inputTopicQA.key }
                if (topicQA == null) {
                    topicQA = TopicQAEntity(
                        key = inputTopicQA.key,
                        name = inputTopicQA.name,
                        answersArtifact = artifact
                    ).apply {
                        this.parentChain = inputTopicQA.parentChain
                    }
                    artifact.topicsWithQA.add(topicQA)
                }

                topicQA.entries.add(
                    QAEntryEntity(
                        questionText = entry.questionText,
                        level = entry.level,
                        answer = answer,
                        topicQA = topicQA
                    )
                )

                pipelineService.saveAndFlush(pipelineStep.pipeline)
                prepareIncrementalYaml(artifact)
            }

            artifactStorage.saveAnswersArtifact(pipelineStep.pipeline.topicKey, pipelineStep.pipeline.name, yamlContent)
            loggerService.log( pipelineStep, "Saved answer for topic: [${inputTopicQA.name}], question: [${entry.questionText.take(50)}]")
        }
    }

    private fun prepareIncrementalYaml(artifact: AnswersArtifactEntity): String {
        val totalAnswers = artifact.topicsWithQA.sumOf { it.entries.size }
        return yamlMapper.writeValueAsString(
            mapOf(
                "totalAnswers" to totalAnswers,
                "topics" to artifact.topicsWithQA.map { topicQA ->
                    mapOf(
                        "key" to topicQA.key,
                        "name" to topicQA.name,
                        "parentChain" to topicQA.parentChain,
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
        ).trim()
    }

    private fun interpolateAnswerPrompt(prompt: String, topicQA: TopicQAEntity, entry: QAEntryEntity): String {
        return prompt
            .replace("{{topicName}}", topicQA.name)
            .replace("{{parentChain}}", topicQA.parentChain ?: "")
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
