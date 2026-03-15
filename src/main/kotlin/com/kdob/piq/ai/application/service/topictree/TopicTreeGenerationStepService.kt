package com.kdob.piq.ai.application.service.topictree

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.GoogleAiChatService
import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.TopicTreeNode
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNodeEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.LinkedList

@Service
class TopicTreeGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(TopicTreeGenerationStepService::class.java)

    companion object {
        const val DEFAULT_MAX_DEPTH = 3
    }

    override fun getStepType(): String = "TOPIC_TREE_GENERATION"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline
        val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Root topic not found: ${pipeline.topicKey}")

        val maxDepth = DEFAULT_MAX_DEPTH
        val allNodes = mutableListOf<TopicTreeNode>()

        val rootNode = TopicTreeNode(
            key = topicDetail.key,
            name = topicDetail.name,
            parentTopicKey = null,
            coverageArea = topicDetail.coverageArea,
            depth = 0,
            leaf = false
        )

        val queue: LinkedList<TopicTreeNode> = LinkedList()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val current = queue.poll()

            if (current.depth >= maxDepth) {
                allNodes.add(current.copy(leaf = true))
                continue
            }

            val parentChain = buildParentChain(current, allNodes, rootNode)
            val siblingTopics = allNodes
                .filter { it.parentTopicKey == current.parentTopicKey && it.key != current.key }
                .joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }

            val systemPrompt = interpolate(
                step.systemPrompt?.content ?: "",
                current, parentChain, siblingTopics, maxDepth
            )
            val userPrompt = interpolate(
                step.userPrompt?.content ?: "",
                current, parentChain, siblingTopics, maxDepth
            )

            logger.info("Generating subtopics for: {} (depth: {})", current.name, current.depth)
            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            val subtopics = parseTopicTreeNodes(rawOutput, current.key, current.depth + 1)

            if (subtopics.isEmpty()) {
                allNodes.add(current.copy(leaf = true))
                continue
            }

            allNodes.add(current)

            for (subtopic in subtopics) {
                if (subtopic.leaf || subtopic.depth >= maxDepth) {
                    allNodes.add(subtopic.copy(leaf = true))
                } else {
                    queue.add(subtopic)
                }
            }
        }

        saveTopicTreeArtifact(pipeline, step, allNodes, maxDepth)
    }

    private fun buildParentChain(
        current: TopicTreeNode,
        allNodes: List<TopicTreeNode>,
        rootNode: TopicTreeNode
    ): String {
        val chain = mutableListOf<TopicTreeNode>()
        var parentKey = current.parentTopicKey
        while (parentKey != null) {
            val parent = allNodes.find { it.key == parentKey } ?: if (rootNode.key == parentKey) rootNode else break
            chain.add(0, parent)
            parentKey = parent.parentTopicKey
        }
        if (chain.isEmpty() || chain.first().key != rootNode.key) {
            chain.add(0, rootNode)
        }
        chain.add(current)

        return chain.joinToString("\n") { "- ${it.name} (depth: ${it.depth}): ${it.coverageArea}" }
    }

    private fun interpolate(
        prompt: String,
        topic: TopicTreeNode,
        parentChain: String,
        siblingTopics: String,
        maxDepth: Int
    ): String {
        return prompt
            .replace("{{topicKey}}", topic.key)
            .replace("{{topicName}}", topic.name)
            .replace("{{coverageArea}}", topic.coverageArea)
            .replace("{{parentChain}}", parentChain)
            .replace("{{siblingTopics}}", siblingTopics.ifBlank { "None" })
            .replace("{{depth}}", topic.depth.toString())
            .replace("{{maxDepth}}", maxDepth.toString())
            .replace("{{parentKey}}", topic.key)
    }

    private fun parseTopicTreeNodes(rawOutput: String, parentKey: String, depth: Int): List<TopicTreeNode> {
        val data = parseYaml(rawOutput)
        @Suppress("UNCHECKED_CAST")
        val topicsList = data["topics"] as? List<Map<String, Any>> ?: emptyList()

        return topicsList.map {
            TopicTreeNode(
                key = it["key"] as String,
                name = it["name"] as String,
                parentTopicKey = it["parentTopicKey"] as? String ?: parentKey,
                coverageArea = it["coverageArea"] as String,
                depth = depth,
                leaf = it["leaf"] as? Boolean ?: false
            )
        }
    }

    private fun saveTopicTreeArtifact(
        pipeline: com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity,
        step: PipelineStepEntity,
        nodes: List<TopicTreeNode>,
        maxDepth: Int
    ) {
        clearOldArtifact(pipeline, step)

        val artifact = TopicTreeArtifactEntity(pipeline = pipeline, maxDepth = maxDepth)
        artifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
        artifact.nodes.addAll(nodes.map { it.toTopicTreeNodeEntity(artifact) })

        step.artifact = artifact
        updatePipeline(pipeline, PipelineStatus.WAITING_ARTIFACT_APPROVAL)

        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "rootTopicKey" to pipeline.topicKey,
                "totalTopics" to nodes.size,
                "maxDepth" to maxDepth,
                "topics" to nodes.map { node ->
                    mapOf(
                        "key" to node.key,
                        "name" to node.name,
                        "parentTopicKey" to node.parentTopicKey,
                        "coverageArea" to node.coverageArea,
                        "depth" to node.depth,
                        "leaf" to node.leaf
                    )
                }
            )
        )
        artifactStorage.saveTopicTreeArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())

        logger.info(
            "Topic tree generated for pipeline '{}': {} topics, max depth {}",
            pipeline.name, nodes.size, maxDepth
        )
    }
}
