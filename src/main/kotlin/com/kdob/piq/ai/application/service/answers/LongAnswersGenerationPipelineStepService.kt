package com.kdob.piq.ai.application.service.answers

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

private const val LONG_ANSWERS_GENERATION_STEP_TYPE = "LONG_ANSWERS_GENERATION"

@Service
class LongAnswersGenerationPipelineStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage, generationLogRepository, transactionManager) {

    override fun getStepType(): String = LONG_ANSWERS_GENERATION_STEP_TYPE

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        initializeArtifact(pipelineId, step)

        while (true) {
            if (isPipelineStopped(pipelineId, step.stepOrder)) return

            val nextTopicQA = findNextTopicToGenerate(pipelineId, step.id!!)
            if (nextTopicQA == null) {
                log(pipelineId, step.stepOrder, "Long Answers Generation completed successfully.")
                finalizeArtifact(pipelineId)
                return
            }

            try {
                generateForTopic(pipelineId, step.id!!, step.stepOrder, nextTopicQA)
            } catch (e: Exception) {
                log(pipelineId, step.stepOrder, "Error during generation for ${nextTopicQA.name}: ${e.message}")
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

            @Suppress("UNCHECKED_CAST")
            val questions = t["questions"] as? List<Map<String, Any>> ?: emptyList()
            val existing = existingByKey[key]
            if (existing != null) {
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
                )
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
    override fun initializeArtifactInternal(pipelineId: Long, stepId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!

            val questionsStep = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }
                ?: throw IllegalStateException("QUESTIONS_GENERATION step not found for pipeline: ${pipeline.name}")
            val questionsArtifact = questionsStep.artifact as? AnswersArtifactEntity
                ?: throw IllegalStateException("Questions artifact not found for pipeline: ${pipeline.name}")

            check(questionsArtifact.status == ArtifactStatus.APPROVED) {
                "Questions artifact is not APPROVED. Current status: ${questionsArtifact.status}"
            }

            val artifact = AnswersArtifactEntity(pipeline = pipeline)
            artifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
            step.artifact = artifact

            pipelineRepository.saveAndFlush(pipeline)
            saveIncrementalYaml(pipeline, artifact)
            log(pipelineId, step.stepOrder, "Initialized Long Answers Artifact.")
        }
    }

    private fun findNextTopicToGenerate(pipelineId: Long, stepId: Long): TopicQAEntity? {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            val questionsStep = pipeline.steps.find { it.stepType == "QUESTIONS_GENERATION" }!!
            val questionsArtifact = questionsStep.artifact as AnswersArtifactEntity

            val generatedTopicKeys = artifact.topicsWithQA.map { it.key }.toSet()
            questionsArtifact.topicsWithQA.find { it.key !in generatedTopicKeys }
                ?.also { it.entries.size } // force-initialize lazy collection before session closes
        }
    }

    private fun generateForTopic(pipelineId: Long, stepId: Long, stepOrder: Int, inputTopicQA: TopicQAEntity) {
        log(
            pipelineId,
            stepOrder,
            "Generating long answers for topic: ${inputTopicQA.name} (${inputTopicQA.entries.size} questions)"
        )

        val (systemPromptTemplate, userPromptTemplate) = getStepPrompts(pipelineId, stepId)
        val newEntries = mutableListOf<QAEntryEntity>()

        for (entry in inputTopicQA.entries) {
            val systemPrompt = interpolateAnswerPrompt(systemPromptTemplate, inputTopicQA, entry)
            val userPrompt = interpolateAnswerPrompt(userPromptTemplate, inputTopicQA, entry)

            getLogger().info("Generating answer for: {} [{}]", entry.questionText.take(80), entry.level)
            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            val answer = parseAnswer(rawOutput)

            newEntries.add(
                QAEntryEntity(
                    questionText = entry.questionText,
                    level = entry.level,
                    answer = answer,
                    topicQA = inputTopicQA // temporary, will be replaced when saving
                )
            )
        }

        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            val newTopicQA = TopicQAEntity(
                key = inputTopicQA.key,
                name = inputTopicQA.name,
                answersArtifact = artifact
            )

            newTopicQA.entries.addAll(newEntries.map { entry ->
                QAEntryEntity(
                    questionText = entry.questionText,
                    level = entry.level,
                    answer = entry.answer,
                    topicQA = newTopicQA
                )
            })

            artifact.topicsWithQA.add(newTopicQA)

            pipelineRepository.saveAndFlush(pipeline)
            saveIncrementalYaml(pipeline, artifact)
            log(pipelineId, stepOrder, "Saved ${newEntries.size} answers for topic: ${inputTopicQA.name}")
        }
    }

    private fun saveIncrementalYaml(pipeline: PipelineEntity, artifact: AnswersArtifactEntity) {
        val totalAnswers = artifact.topicsWithQA.sumOf { it.entries.size }
        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "totalAnswers" to totalAnswers,
                "topics" to artifact.topicsWithQA.map { topicQA ->
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
