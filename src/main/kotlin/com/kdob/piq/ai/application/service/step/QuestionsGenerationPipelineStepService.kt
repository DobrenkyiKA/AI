package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import java.util.concurrent.ConcurrentHashMap

private const val QUESTIONS_GENERATION_STEP_TYPE = "QUESTIONS_GENERATION"

@Service
class QuestionsGenerationPipelineStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService,
    private val questionCatalogClient: QuestionCatalogClient
) : AbstractPipelineStepService(
    pipelineService,
    artifactStorage,
    pipelineStatusService,
    transactionManager,
    loggerService,
    generator,
    pipelineArtifactStatusService
) {
    private val catalogChainCache = ConcurrentHashMap<String, List<CatalogTopicInfo>>()

    data class CatalogTopicInfo(val key: String, val name: String)

    override fun getStepType(): String = QUESTIONS_GENERATION_STEP_TYPE

    override fun generate(pipelineStep: PipelineStepEntity) {
        initializeArtifact(pipelineStep)

        while (true) {
            if (pipelineStatusService.isStopped(pipelineStep)) return

            val nextTopic = findNextTopicToGenerate(pipelineStep.pipeline.name, pipelineStep.id!!)
            if (nextTopic == null) {
                loggerService.log(pipelineStep, "Questions Generation completed successfully.")
                finalizeArtifact(pipelineStep)
                return
            }

            try {
                generateForTopic(pipelineStep, nextTopic)
            } catch (e: Exception) {
                loggerService.log(pipelineStep, "Error during generation for ${nextTopic.name}: ${e.message}")
                throw e
            }
        }
    }

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        val artifact = step.artifact as? AnswersArtifactEntity ?: throw IllegalStateException("Answers artifact not found")
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

    override fun initializeArtifactInternal(pipelineStep: PipelineStepEntity) {
        transactionTemplate.execute {
            val topicTreeStep = pipelineStep.pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }
                ?: throw IllegalStateException("TOPIC_TREE_GENERATION step not found for pipeline: ${pipelineStep.pipeline.name}")
            val topicTreeArtifact = topicTreeStep.artifact as? TopicTreeArtifactEntity
                ?: throw IllegalStateException("Topic tree artifact not found for pipeline: ${pipelineStep.pipeline.name}")

            check(topicTreeArtifact.status == ArtifactStatus.APPROVED) {
                "Topic tree artifact is not APPROVED. Current status: ${topicTreeArtifact.status}"
            }

            val artifact = AnswersArtifactEntity(pipeline = pipelineStep.pipeline)
            artifact.status = ArtifactStatus.GENERATION_IN_PROGRESS
            pipelineStep.artifact = artifact

            pipelineService.saveAndFlush(pipelineStep.pipeline)
            val yamlContent = prepareIncrementalYaml(artifact)
            artifactStorage.saveQuestionsArtifact(
                pipelineStep.pipeline.topicKey,
                pipelineStep.pipeline.name,
                yamlContent
            )
            loggerService.log(pipelineStep, "Initialized Answers Artifact.")
        }
    }

    private fun findNextTopicToGenerate(pipelineName: String, stepId: Long): TopicTreeNodeEntity? {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineService.get(pipelineName)
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as AnswersArtifactEntity

            val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val generatedTopicKeys = artifact.topicsWithQA.map { it.key }.toSet()
            topicTreeArtifact.nodes.find { it.key !in generatedTopicKeys }
        }
    }

    private fun generateForTopic(pipelineStep: PipelineStepEntity, node: TopicTreeNodeEntity) {
        val (systemPrompt, userPrompt, parentChain) = transactionTemplate.execute {
            val topicTreeStep = pipelineStep.pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val isLeaf = node.leaf
            val nodesByParent = topicTreeArtifact.nodes.groupBy { it.parentTopicKey }
            val children = nodesByParent[node.key] ?: emptyList()
            val parentChain = buildParentChain(node, topicTreeArtifact.nodes, pipelineStep.pipeline.topicKey)

            val sys = interpolateQuestionPrompt(
                pipelineStep.systemPrompt?.content ?: "", node, isLeaf, children, parentChain
            )
            val usr = interpolateQuestionPrompt(
                pipelineStep.userPrompt?.content ?: "", node, isLeaf, children, parentChain
            )
            Triple(sys, usr, parentChain)
        }

        loggerService.log(pipelineStep, "Generating questions for topic: ${node.name} (leaf: ${node.leaf})")
        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val questions = parseQuestionsWithLevels(rawOutput)

        val (topicKey, pipelineName, yamlContent) = transactionTemplate.execute {
            val artifact = pipelineStep.artifact as AnswersArtifactEntity

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

            pipelineService.saveAndFlush(pipelineStep.pipeline)
            Triple(pipelineStep.pipeline.topicKey, pipelineStep.pipeline.name, prepareIncrementalYaml(artifact))
        }

        artifactStorage.saveQuestionsArtifact(topicKey, pipelineName, yamlContent)
        loggerService.log(pipelineStep, "Saved ${questions.size} questions for topic: ${node.name}")
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
            val headerAndMarkerRegex = Regex(
                """Subtopics \(for branch topics, generate cross-cutting questions\):\s*NONE_MARKER\s*""",
                RegexOption.IGNORE_CASE
            )
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

    fun getArtifact(pipelineEntity: PipelineEntity): AnswersArtifactEntity {
        return (pipelineEntity.steps.find { it.stepType == QUESTIONS_GENERATION_STEP_TYPE }?.artifact as? AnswersArtifactEntity)!!
    }
}
