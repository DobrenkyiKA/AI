package com.kdob.piq.ai.application.service.questions

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
class QuestionsGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(QuestionsGenerationStepService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun getStepType(): String = "QUESTIONS_GENERATION"

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        var artifact = transactionTemplate.execute {
            val p = pipelineRepository.findById(pipelineId)!!
            val s: PipelineStepEntity = p.steps.find { it.id == step.id }!!
            s.artifact as? AnswersArtifactEntity
        }

        if (artifact == null) {
            log(pipelineId, "Starting new Questions Generation...")
            artifact = initializeArtifact(pipelineId, step.id!!)
        } else {
            log(pipelineId, "Resuming Questions Generation...")
        }

        while (true) {
            val currentPipeline = pipelineRepository.findById(pipelineId)!!
            if (currentPipeline.status == PipelineStatus.PAUSED) {
                log(pipelineId, "Generation PAUSED by user.")
                return
            }
            if (currentPipeline.status == PipelineStatus.ABORTED) {
                log(pipelineId, "Generation ABORTED by user.")
                return
            }

            val nextTopic = findNextTopicToGenerate(pipelineId, step.id!!)
            if (nextTopic == null) {
                log(pipelineId, "Questions Generation completed successfully.")
                finalizeArtifact(pipelineId, step.id!!)
                return
            }

            try {
                generateForTopic(pipelineId, step.id!!, nextTopic)
            } catch (e: Exception) {
                log(pipelineId, "Error during generation for ${nextTopic.name}: ${e.message}")
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
                    topicQA = topicQA
                )
            })
            artifact.topicsWithQA.add(topicQA)
        }
        
        artifactStorage.saveQuestionsArtifact(step.pipeline.topicKey, step.pipeline.name, yamlContent.trim())
    }

    private fun initializeArtifact(pipelineId: Long, stepId: Long): AnswersArtifactEntity {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!

            val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }
                ?: throw IllegalStateException("TOPIC_TREE_GENERATION step not found for pipeline: ${pipeline.name}")
            val topicTreeArtifact = topicTreeStep.artifact as? TopicTreeArtifactEntity
                ?: throw IllegalStateException("Topic tree artifact not found for pipeline: ${pipeline.name}")

            check(topicTreeArtifact.status == ArtifactStatus.APPROVED) {
                "Topic tree artifact is not APPROVED. Current status: ${topicTreeArtifact.status}"
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

    private fun findNextTopicToGenerate(pipelineId: Long, stepId: Long): TopicTreeNodeEntity? {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val generatedTopicKeys = artifact.topicsWithQA.map { it.key }.toSet()
            topicTreeArtifact.nodes.find { it.key !in generatedTopicKeys }
        }
    }

    private fun generateForTopic(pipelineId: Long, stepId: Long, node: TopicTreeNodeEntity) {
        val (systemPrompt, userPrompt) = transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!

            val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val isLeaf = node.leaf
            val nodesByParent = topicTreeArtifact.nodes.groupBy { it.parentTopicKey }
            val children = nodesByParent[node.key] ?: emptyList()
            val parentChain = buildParentChain(node, topicTreeArtifact.nodes)

            val sys = interpolateQuestionPrompt(
                step.systemPrompt?.content ?: "", node, isLeaf, children, parentChain
            )
            val usr = interpolateQuestionPrompt(
                step.userPrompt?.content ?: "", node, isLeaf, children, parentChain
            )
            Pair(sys, usr)
        }!!

        log(pipelineId, "Generating questions for topic: ${node.name} (leaf: ${node.leaf})")
        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val questions = parseQuestionsWithLevels(rawOutput)

        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            if (questions.isNotEmpty()) {
                val topicQA = TopicQAEntity(
                    key = node.key,
                    name = node.name,
                    answersArtifact = artifact
                )
                topicQA.entries.addAll(questions.map { (text, level) ->
                    QAEntryEntity(
                        questionText = text,
                        level = level,
                        topicQA = topicQA
                    )
                })
                artifact.topicsWithQA.add(topicQA)
            }

            pipelineRepository.saveAndFlush(pipeline)
            saveIncrementalYaml(pipeline, artifact)
            log(pipelineId, "Saved ${questions.size} questions for topic: ${node.name}")
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
        val totalQuestions = artifact.topicsWithQA.sumOf { it.entries.size }
        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "totalQuestions" to totalQuestions,
                "topics" to artifact.topicsWithQA.map { topicQA ->
                    mapOf(
                        "key" to topicQA.key,
                        "name" to topicQA.name,
                        "questions" to topicQA.entries.map { entry ->
                            mapOf(
                                "text" to entry.questionText,
                                "level" to entry.level
                            )
                        }
                    )
                }
            )
        )
        artifactStorage.saveQuestionsArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())
    }

    private fun buildParentChain(node: TopicTreeNodeEntity, allNodes: Set<TopicTreeNodeEntity>): String {
        val chain = mutableListOf<TopicTreeNodeEntity>()
        var parentKey = node.parentTopicKey
        while (parentKey != null) {
            val parent = allNodes.find { it.key == parentKey } ?: break
            chain.add(0, parent)
            parentKey = parent.parentTopicKey
        }
        return chain.joinToString(" > ") { it.name }
    }

    private fun interpolateQuestionPrompt(
        prompt: String,
        node: TopicTreeNodeEntity,
        isLeaf: Boolean,
        children: List<TopicTreeNodeEntity>,
        parentChain: String
    ): String {
        val topicType = if (isLeaf) "leaf" else "branch"
        val childTopicsList = if (children.isNotEmpty()) {
            children.joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }
        } else {
            "None"
        }

        return prompt
            .replace("{{topicKey}}", node.key)
            .replace("{{topicName}}", node.name)
            .replace("{{coverageArea}}", node.coverageArea)
            .replace("{{topicType}}", topicType)
            .replace("{{childTopicsList}}", childTopicsList)
            .replace("{{parentChain}}", parentChain.ifBlank { "Root" })
    }

    private fun parseQuestionsWithLevels(rawOutput: String): List<Pair<String, String>> {
        val data = parseYaml(rawOutput)
        @Suppress("UNCHECKED_CAST")
        val questionsList = data["questions"] as? List<Any> ?: emptyList()

        return questionsList.mapNotNull { item ->
            when (item) {
                is Map<*, *> -> {
                    val text = item["text"] as? String ?: return@mapNotNull null
                    val level = item["level"] as? String ?: "mid"
                    text to level
                }
                is String -> item to "mid"
                else -> null
            }
        }
    }
}
