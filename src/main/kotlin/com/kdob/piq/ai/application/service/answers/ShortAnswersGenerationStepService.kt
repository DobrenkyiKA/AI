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
import org.springframework.transaction.support.TransactionTemplate

@Service
class ShortAnswersGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(ShortAnswersGenerationStepService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun getStepType(): String = "SHORT_ANSWERS_GENERATION"

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        var artifact = transactionTemplate.execute {
            val p = pipelineRepository.findById(pipelineId)!!
            val s: PipelineStepEntity = p.steps.find { it.id == step.id }!!
            s.artifact as? AnswersArtifactEntity
        }

        if (artifact == null) {
            log(pipelineId, step.stepOrder, "Starting new Short Answers Generation...")
            artifact = initializeArtifact(pipelineId, step.id!!, step.stepOrder)
        } else {
            log(pipelineId, step.stepOrder, "Resuming Short Answers Generation...")
        }

        while (true) {
            val currentPipeline = pipelineRepository.findById(pipelineId)!!
            if (currentPipeline.status == PipelineStatus.PAUSED) {
                log(pipelineId, step.stepOrder, "Generation PAUSED by user.")
                return
            }
            if (currentPipeline.status == PipelineStatus.ABORTED) {
                log(pipelineId, step.stepOrder, "Generation ABORTED by user.")
                return
            }

            val nextTopicQA = findNextTopicToGenerate(pipelineId, step.id!!)
            if (nextTopicQA == null) {
                log(pipelineId, step.stepOrder, "Short Answers Generation completed successfully.")
                finalizeArtifact(pipelineId, step.id!!)
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
        
        artifact.topicsWithQA.clear()
        for (t in topicsList) {
            val topicQA = TopicQAEntity(
                key = t["key"] as String,
                name = t["name"] as String,
                answersArtifact = artifact
            )
            @Suppress("UNCHECKED_CAST")
            val questions = t["questions"] as? List<Map<String, Any>> ?: emptyList()
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
        
        artifactStorage.saveShortAnswersArtifact(step.pipeline.topicKey, step.pipeline.name, yamlContent.trim())
    }

    private fun initializeArtifact(pipelineId: Long, stepId: Long, stepOrder: Int): AnswersArtifactEntity {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!

            val answersStep = pipeline.steps.find { it.stepType == "LONG_ANSWERS_GENERATION" }
                ?: throw IllegalStateException("LONG_ANSWERS_GENERATION step not found for pipeline: ${pipeline.name}")
            val answersArtifact = answersStep.artifact as? AnswersArtifactEntity
                ?: throw IllegalStateException("Answers artifact not found for pipeline: ${pipeline.name}")

            check(answersArtifact.status == ArtifactStatus.APPROVED) {
                "Answers artifact is not APPROVED. Current status: ${answersArtifact.status}"
            }

            val artifact = AnswersArtifactEntity(pipeline = pipeline)
            artifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
            step.artifact = artifact

            pipelineRepository.saveAndFlush(pipeline)
            saveIncrementalYaml(pipeline, artifact)
            log(pipelineId, stepOrder, "Initialized Short Answers Artifact.")
            artifact
        }!!
    }

    private fun findNextTopicToGenerate(pipelineId: Long, stepId: Long): TopicQAEntity? {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            val longAnswersStep = pipeline.steps.find { it.stepType == "LONG_ANSWERS_GENERATION" }!!
            val longAnswersArtifact = longAnswersStep.artifact as AnswersArtifactEntity

            val generatedTopicKeys = artifact.topicsWithQA.map { it.key }.toSet()
            longAnswersArtifact.topicsWithQA.find { it.key !in generatedTopicKeys }
        }
    }

    private fun generateForTopic(pipelineId: Long, stepId: Long, stepOrder: Int, inputTopicQA: TopicQAEntity) {
        log(pipelineId, stepOrder, "Generating short answers for topic: ${inputTopicQA.name} (${inputTopicQA.entries.size} questions)")

        val newEntries = mutableListOf<QAEntryEntity>()

        for (entry in inputTopicQA.entries) {
            val systemPrompt = interpolateShortAnswerPrompt(
                transactionTemplate.execute {
                    val p = pipelineRepository.findById(pipelineId)!!
                    val s: PipelineStepEntity = p.steps.find { it.id == stepId }!!
                    s.systemPrompt?.content ?: ""
                }!!, inputTopicQA, entry
            )
            val userPrompt = interpolateShortAnswerPrompt(
                transactionTemplate.execute {
                    val p = pipelineRepository.findById(pipelineId)!!
                    val s: PipelineStepEntity = p.steps.find { it.id == stepId }!!
                    s.userPrompt?.content ?: ""
                }!!, inputTopicQA, entry
            )

            logger.info("Generating short answer for: {} [{}]", entry.questionText.take(80), entry.level)
            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            val shortAnswer = parseShortAnswer(rawOutput)

            newEntries.add(
                QAEntryEntity(
                    questionText = entry.questionText,
                    level = entry.level,
                    answer = entry.answer,
                    shortAnswer = shortAnswer,
                    topicQA = inputTopicQA // temporary
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
                    shortAnswer = entry.shortAnswer,
                    topicQA = newTopicQA
                )
            })

            artifact.topicsWithQA.add(newTopicQA)

            pipelineRepository.saveAndFlush(pipeline)
            saveIncrementalYaml(pipeline, artifact)
            log(pipelineId, stepOrder, "Saved ${newEntries.size} short answers for topic: ${inputTopicQA.name}")
        }
    }

    private fun finalizeArtifact(pipelineId: Long, stepId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            updatePipeline(pipeline)
        }
    }

    private fun log(pipelineId: Long, stepOrder: Int, message: String) {
        logger.info("[Pipeline {}] {}", pipelineId, message)
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            generationLogRepository.save(GenerationLogEntity(pipeline, message, stepOrder))
        }
    }

    private fun saveIncrementalYaml(pipeline: PipelineEntity, artifact: AnswersArtifactEntity) {
        val totalEntries = artifact.topicsWithQA.sumOf { it.entries.size }
        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "totalEntries" to totalEntries,
                "topics" to artifact.topicsWithQA.map { topicQA ->
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
