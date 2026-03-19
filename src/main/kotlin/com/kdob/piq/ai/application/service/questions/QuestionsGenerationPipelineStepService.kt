package com.kdob.piq.ai.application.service.questions

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.ai.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

private const val QUESTIONS_GENERATION_STEP_TYPE = "QUESTIONS_GENERATION"

@Service
class QuestionsGenerationPipelineStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient,
    generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage, generationLogRepository, transactionManager) {

    private val catalogChainCache = java.util.concurrent.ConcurrentHashMap<String, List<CatalogTopicInfo>>()

    data class CatalogTopicInfo(val key: String, val name: String)

    override fun getStepType(): String = QUESTIONS_GENERATION_STEP_TYPE

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        initializeArtifact(pipelineId, step)
        while (true) {
            if (isPipelineStopped(pipelineId, step.stepOrder)) return

            val nextTopic = findNextTopicToGenerate(pipelineId, step.id!!)
            if (nextTopic == null) {
                log(pipelineId, step.stepOrder, "Questions Generation completed successfully.")
                finalizeArtifact(pipelineId, step.id!!)
                return
            }

            try {
                generateForTopic(pipelineId, step.id!!, step.stepOrder, nextTopic)
            } catch (e: Exception) {
                log(pipelineId, step.stepOrder, "Error during generation for ${nextTopic.name}: ${e.message}")
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

        val incomingKeys = topicsList.map { it["key"] as String }.toSet()
        artifact.topicsWithQA.removeIf { it.key !in incomingKeys }
        for (t in topicsList) {
            val key = t["key"] as String
            val name = t["name"] as String
            val parentChain = t["parentChain"] as? String

            @Suppress("UNCHECKED_CAST")
            val questions = t["questions"] as? List<Map<String, Any>> ?: emptyList()
            val existing = artifact.topicsWithQA.find { it.key == key }
            if (existing != null) {
                existing.parentChain = parentChain
                existing.entries.clear()
                existing.entries.addAll(questions.map { q ->
                    QAEntryEntity(
                        questionText = q["text"] as String,
                        level = q["level"] as String,
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
                        topicQA = topicQA
                    )
                })
                artifact.topicsWithQA.add(topicQA)
            }
        }

        artifactStorage.saveQuestionsArtifact(step.pipeline.topicKey, step.pipeline.name, yamlContent.trim())
    }
    override fun initializeArtifactInternal(pipelineId: Long, stepId: Long) {
        transactionTemplate.execute {
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
            artifact.status = ArtifactStatus.GENERATION_IN_PROGRESS
            step.artifact = artifact

            pipelineRepository.saveAndFlush(pipeline)
            val yamlContent = prepareIncrementalYaml(artifact)
            artifactStorage.saveQuestionsArtifact(pipeline.topicKey, pipeline.name, yamlContent)
            log(pipelineId, step.stepOrder, "Initialized Answers Artifact.")
        }
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

    private fun generateForTopic(pipelineId: Long, stepId: Long, stepOrder: Int, node: TopicTreeNodeEntity) {
        val (systemPrompt, userPrompt, parentChain) = transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!

            val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val isLeaf = node.leaf
            val nodesByParent = topicTreeArtifact.nodes.groupBy { it.parentTopicKey }
            val children = nodesByParent[node.key] ?: emptyList()
            val parentChain = buildParentChain(node, topicTreeArtifact.nodes, pipeline.topicKey)

            val sys = interpolateQuestionPrompt(
                step.systemPrompt?.content ?: "", node, isLeaf, children, parentChain
            )
            val usr = interpolateQuestionPrompt(
                step.userPrompt?.content ?: "", node, isLeaf, children, parentChain
            )
            Triple(sys, usr, parentChain)
        }

        log(pipelineId, stepOrder, "Generating questions for topic: ${node.name} (leaf: ${node.leaf})")
        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val questions = parseQuestionsWithLevels(rawOutput)

        val (topicKey, pipelineName, yamlContent) = transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            if (questions.isNotEmpty()) {
                val topicQA = TopicQAEntity(
                    key = node.key,
                    name = node.name,
                    answersArtifact = artifact
                ).apply {
                    this.parentChain = parentChain
                }
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
            Triple(pipeline.topicKey, pipeline.name, prepareIncrementalYaml(artifact))
        }

        artifactStorage.saveQuestionsArtifact(topicKey, pipelineName, yamlContent)
        log(pipelineId, stepOrder, "Saved ${questions.size} questions for topic: ${node.name}")
    }

    private fun prepareIncrementalYaml(artifact: AnswersArtifactEntity): String {
        val totalQuestions = artifact.topicsWithQA.sumOf { it.entries.size }
        return yamlMapper.writeValueAsString(
            mapOf(
                "totalQuestions" to totalQuestions,
                "topics" to artifact.topicsWithQA.map { topicQA ->
                    mapOf(
                        "key" to topicQA.key,
                        "name" to topicQA.name,
                        "parentChain" to topicQA.parentChain,
                        "questions" to topicQA.entries.map { entry ->
                            mapOf(
                                "text" to entry.questionText,
                                "level" to entry.level
                            )
                        }
                    )
                }
            )
        ).trim()
    }

    private fun buildParentChain(
        node: TopicTreeNodeEntity,
        allNodes: Set<TopicTreeNodeEntity>,
        pipelineRootKey: String
    ): String {
        val chain = mutableListOf<String>()
        var parentKey = node.parentTopicKey
        while (parentKey != null) {
            val parent = allNodes.find { it.key == parentKey } ?: break
            chain.add(0, parent.name)
            parentKey = parent.parentTopicKey
        }

        val catalogParents = getCatalogParentChain(pipelineRootKey)
        val catalogNames = catalogParents.map { it.name }

        return (catalogNames + chain).joinToString(" > ")
    }

    private fun getCatalogParentChain(rootKey: String): List<CatalogTopicInfo> {
        return catalogChainCache.getOrPut(rootKey) {
            val rootTopic = questionCatalogClient.findTopic(rootKey) ?: return@getOrPut emptyList()
            val pathParts = rootTopic.path.split("/").filter { it.isNotEmpty() }
            val rootIndex = pathParts.indexOf(rootKey)
            if (rootIndex <= 0) return@getOrPut emptyList()

            pathParts.subList(0, rootIndex).map { key ->
                val topic = questionCatalogClient.findTopic(key)
                CatalogTopicInfo(key = key, name = topic?.name ?: key)
            }
        }
    }

    private fun interpolateQuestionPrompt(
        prompt: String,
        node: TopicTreeNodeEntity,
        isLeaf: Boolean,
        children: List<TopicTreeNodeEntity>,
        parentChain: String
    ): String {
        val topicType = if (isLeaf) "leaf" else "branch"
        val childTopicsListPlaceholder = "{{childTopicsList}}"
        val childTopicsList = if (children.isNotEmpty()) {
            children.joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }
        } else {
            "NONE_MARKER"
        }

        var result = prompt
            .replace("{{topicKey}}", node.key)
            .replace("{{topicName}}", node.name)
            .replace("{{coverageArea}}", node.coverageArea)
            .replace("{{topicType}}", topicType)
            .replace(childTopicsListPlaceholder, childTopicsList)
            .replace("{{parentChain}}", parentChain.ifBlank { "Root" })

        if (childTopicsList == "NONE_MARKER") {
            // Remove the whole Subtopics section if it's empty, including the header
            val headerAndMarkerRegex = Regex("""Subtopics \(for branch topics, generate cross-cutting questions\):\s*NONE_MARKER\s*""", RegexOption.IGNORE_CASE)
            result = if (headerAndMarkerRegex.containsMatchIn(result)) {
                result.replace(headerAndMarkerRegex, "")
            } else {
                result.replace("NONE_MARKER", "")
            }
        }

        return result.trim()
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
