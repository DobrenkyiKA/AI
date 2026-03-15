package com.kdob.piq.ai.application.service.answers

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class LongAnswersGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(LongAnswersGenerationStepService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun getStepType(): String = "LONG_ANSWERS_GENERATION"

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        var artifact = transactionTemplate.execute {
            val p = pipelineRepository.findById(pipelineId)!!
            val s: PipelineStepEntity = p.steps.find { it.id == step.id }!!
            s.artifact as? AnswersArtifactEntity
        }

        if (artifact == null) {
            log(pipelineId, "Starting new Long Answers Generation...")
            artifact = initializeArtifact(pipelineId, step.id!!)
        } else {
            log(pipelineId, "Resuming Long Answers Generation...")
        }

        while (true) {
            val currentPipeline = pipelineRepository.findById(pipelineId)!!
            if (currentPipeline.status == PipelineStatus.GENERATION_PAUSED) {
                log(pipelineId, "Generation PAUSED by user.")
                return
            }
            if (currentPipeline.status == PipelineStatus.GENERATION_ABORTED) {
                log(pipelineId, "Generation ABORTED by user.")
                return
            }

            val nextTopicQA = findNextTopicToGenerate(pipelineId, step.id!!)
            if (nextTopicQA == null) {
                log(pipelineId, "Long Answers Generation completed successfully.")
                finalizeArtifact(pipelineId, step.id!!)
                return
            }

            try {
                generateForTopic(pipelineId, step.id!!, nextTopicQA)
            } catch (e: Exception) {
                log(pipelineId, "Error during generation for ${nextTopicQA.name}: ${e.message}")
                throw e
            }
        }
    }

    private fun initializeArtifact(pipelineId: Long, stepId: Long): AnswersArtifactEntity {
        return transactionTemplate.execute {
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
            log(pipelineId, "Initialized Answers Artifact.")
            artifact
        }!!
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
        }
    }

    private fun generateForTopic(pipelineId: Long, stepId: Long, inputTopicQA: TopicQAEntity) {
        log(pipelineId, "Generating long answers for topic: ${inputTopicQA.name} (${inputTopicQA.entries.size} questions)")

        val newEntries = mutableListOf<QAEntryEntity>()

        for (entry in inputTopicQA.entries) {
            val systemPrompt = interpolateAnswerPrompt(
                transactionTemplate.execute {
                    val p = pipelineRepository.findById(pipelineId)!!
                    val s: PipelineStepEntity = p.steps.find { it.id == stepId }!!
                    s.systemPrompt?.content ?: ""
                }!!, inputTopicQA, entry
            )
            val userPrompt = interpolateAnswerPrompt(
                transactionTemplate.execute {
                    val p = pipelineRepository.findById(pipelineId)!!
                    val s: PipelineStepEntity = p.steps.find { it.id == stepId }!!
                    s.userPrompt?.content ?: ""
                }!!, inputTopicQA, entry
            )

            logger.info("Generating answer for: {} [{}]", entry.questionText.take(80), entry.level)
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
            log(pipelineId, "Saved ${newEntries.size} answers for topic: ${inputTopicQA.name}")
        }
    }

    private fun finalizeArtifact(pipelineId: Long, stepId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            updatePipeline(pipeline, PipelineStatus.WAITING_ARTIFACT_APPROVAL)
        }
    }

    private fun log(pipelineId: Long, message: String) {
        logger.info("[Pipeline {}] {}", pipelineId, message)
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            generationLogRepository.save(GenerationLogEntity(pipeline, message))
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
