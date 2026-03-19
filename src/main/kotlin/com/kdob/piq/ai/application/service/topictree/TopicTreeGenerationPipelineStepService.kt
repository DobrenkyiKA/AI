package com.kdob.piq.ai.application.service.topictree

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.ai.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.TopicTreeNode
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNode
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNodeEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

private const val TOPIC_TREE_GENERATION_STEP_TYPE = "TOPIC_TREE_GENERATION"

@Service
class TopicTreeGenerationPipelineStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient,
    generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage, generationLogRepository, transactionManager) {

    private val catalogChainCache = java.util.concurrent.ConcurrentHashMap<String, List<TopicTreeNode>>()

    companion object {
        const val DEFAULT_MAX_DEPTH = 12
    }

    override fun getStepType(): String = TOPIC_TREE_GENERATION_STEP_TYPE

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        initializeArtifact(pipelineId, step)

        while (true) {
            if (isPipelineStopped(pipelineId, step.stepOrder)) return

            val nodeToExpand = findNodeToExpand(pipelineId, step.id!!)
            if (nodeToExpand == null) {
                log(pipelineId, step.stepOrder, "Topic Tree Generation completed successfully.")
                finalizeArtifact(pipelineId, step.id!!)
                return
            }

            try {
                expandNode(pipelineId, step.id!!, step.stepOrder, nodeToExpand)
            } catch (e: Exception) {
                log(pipelineId, step.stepOrder, "Error during expansion of ${nodeToExpand.name}: ${e.message}")
                throw e
            }
        }
    }

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        val artifact = step.artifact as? TopicTreeArtifactEntity
            ?: throw IllegalStateException("Topic tree artifact not found")
        artifact.status = status

        val data = parseYaml(yamlContent)

        @Suppress("UNCHECKED_CAST")
        val topicsList = data["topics"] as? List<Map<String, Any>> ?: emptyList()

        val incomingNodes = topicsList.map {
            TopicTreeNode(
                key = it["key"] as String,
                name = it["name"] as String,
                parentTopicKey = it["parentTopicKey"] as? String,
                coverageArea = it["coverageArea"] as String,
                depth = (it["depth"] as Number).toInt(),
                leaf = it["leaf"] as? Boolean ?: false
            )
        }
        val incomingKeys = incomingNodes.map { it.key }.toSet()

        // Remove nodes that are no longer present (orphanRemoval will delete them)
        artifact.nodes.removeIf { it.key !in incomingKeys }

        // Update existing nodes in-place and add new ones to avoid Hibernate flush ordering issues
        for (incoming in incomingNodes) {
            val existing = artifact.nodes.find { it.key == incoming.key }
            if (existing != null) {
                existing.name = incoming.name
                existing.parentTopicKey = incoming.parentTopicKey
                existing.coverageArea = incoming.coverageArea
                existing.depth = incoming.depth
                existing.leaf = incoming.leaf
            } else {
                artifact.nodes.add(incoming.toTopicTreeNodeEntity(artifact))
            }
        }

        artifactStorage.saveTopicTreeArtifact(step.pipeline.topicKey, step.pipeline.name, yamlContent.trim())
    }

    override fun initializeArtifactInternal(pipelineId: Long, stepId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!

            val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
                ?: throw IllegalStateException("Root topic not found: ${pipeline.topicKey}")

            val artifact = TopicTreeArtifactEntity(pipeline = pipeline, maxDepth = DEFAULT_MAX_DEPTH)
            artifact.status = ArtifactStatus.GENERATION_IN_PROGRESS

            val rootNode = TopicTreeNode(
                key = topicDetail.key,
                name = topicDetail.name,
                parentTopicKey = null,
                coverageArea = topicDetail.coverageArea,
                depth = 0,
                leaf = false
            )
            artifact.nodes.add(rootNode.toTopicTreeNodeEntity(artifact))
            step.artifact = artifact

            pipelineRepository.saveAndFlush(pipeline)
            val yamlContent = prepareIncrementalYaml(pipeline, artifact)
            artifactStorage.saveTopicTreeArtifact(pipeline.topicKey, pipeline.name, yamlContent)
            log(pipelineId, step.stepOrder, "Initialized Topic Tree with root: ${rootNode.name}")
        }
    }

    private fun findNodeToExpand(pipelineId: Long, stepId: Long): TopicTreeNode? {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as TopicTreeArtifactEntity
            val allNodes = artifact.nodes.toList()
            val parentKeysWithChildren = allNodes.mapNotNull { it.parentTopicKey }.toSet()

            val candidate = allNodes.find {
                !it.leaf && it.depth < artifact.maxDepth && it.key !in parentKeysWithChildren
            }
            candidate?.toTopicTreeNode()
        }
    }

    internal fun expandNode(pipelineId: Long, stepId: Long, stepOrder: Int, node: TopicTreeNode) {
        val (systemPrompt, userPrompt, maxDepth) = transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as TopicTreeArtifactEntity
            val allNodes = artifact.nodes.map { it.toTopicTreeNode() }
            val rootNode = allNodes.find { it.parentTopicKey == null }!!

            val parentChain = buildParentChain(node, allNodes, rootNode)
            val siblingTopics = allNodes
                .filter { it.parentTopicKey == node.parentTopicKey && it.key != node.key }
                .joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }

            val sys = interpolate(step.systemPrompt?.content ?: "", node, parentChain, siblingTopics, artifact.maxDepth)
            val usr = interpolate(step.userPrompt?.content ?: "", node, parentChain, siblingTopics, artifact.maxDepth)
            Triple(sys, usr, artifact.maxDepth)
        }

        log(pipelineId, stepOrder, "Generating subtopics for: ${node.name} (depth: ${node.depth})")

        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val subtopics = parseTopicTreeNodes(rawOutput, node.key, node.depth + 1)

        val (topicKey, pipelineName, yamlContent) = transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as TopicTreeArtifactEntity

            val existingKeys = artifact.nodes.map { it.key }.toSet()
            val validSubtopics = subtopics.filter { it.key !in existingKeys && it.key != node.key }

            if (validSubtopics.isEmpty()) {
                val nodeInDb = artifact.nodes.find { it.key == node.key }!!
                nodeInDb.leaf = true
                log(pipelineId, stepOrder, "No NEW subtopics found for ${node.name}. Marked as leaf.")
            } else {
                for (sub in validSubtopics) {
                    val leaf = sub.leaf || sub.depth >= maxDepth
                    artifact.nodes.add(sub.copy(leaf = leaf).toTopicTreeNodeEntity(artifact))
                }
                log(pipelineId, stepOrder, "Generated ${validSubtopics.size} subtopics for ${node.name}.")
            }

            pipelineRepository.saveAndFlush(pipeline)
            Triple(pipeline.topicKey, pipeline.name, prepareIncrementalYaml(pipeline, artifact))
        }

        artifactStorage.saveTopicTreeArtifact(topicKey, pipelineName, yamlContent)
    }



    private fun prepareIncrementalYaml(pipeline: PipelineEntity, artifact: TopicTreeArtifactEntity): String {
        val nodes = artifact.nodes.map { it.toTopicTreeNode() }.sortedWith(compareBy({ it.depth }, { it.key }))
        val yamlData = mapOf(
            "rootTopicKey" to pipeline.topicKey,
            "totalTopics" to nodes.size,
            "maxDepth" to artifact.maxDepth,
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
        return yamlMapper.writeValueAsString(yamlData).trim()
    }

    private fun getCatalogParentChain(rootKey: String): List<TopicTreeNode> {
        return catalogChainCache.getOrPut(rootKey) {
            val rootTopic = questionCatalogClient.findTopic(rootKey) ?: return@getOrPut emptyList()
            val pathParts = rootTopic.path.split("/").filter { it.isNotEmpty() }
            val rootIndex = pathParts.indexOf(rootKey)
            if (rootIndex <= 0) return@getOrPut emptyList()

            pathParts.subList(0, rootIndex).mapIndexed { index, key ->
                val topic = questionCatalogClient.findTopic(key)
                TopicTreeNode(
                    key = key,
                    name = topic?.name ?: key,
                    coverageArea = topic?.coverageArea ?: "",
                    depth = index - rootIndex,
                    leaf = false,
                    parentTopicKey = if (index > 0) pathParts[index - 1] else null
                )
            }
        }
    }

    private fun buildParentChain(
        current: TopicTreeNode,
        allNodes: List<TopicTreeNode>,
        rootNode: TopicTreeNode
    ): String {
        val chain = mutableListOf<TopicTreeNode>()
        var curr: TopicTreeNode? = current
        val visited = mutableSetOf<String>()

        while (curr != null && curr.key !in visited) {
            visited.add(curr.key)
            chain.add(0, curr)
            if (curr.key == rootNode.key) break
            val parentKey = curr.parentTopicKey
            curr = if (parentKey != null) {
                allNodes.find { it.key == parentKey }
            } else null
        }

        // Remove the current node from the chain as it's displayed separately in "Current Topic to Decompose"
        chain.removeIf { it.key == current.key }

        val catalogParents = getCatalogParentChain(rootNode.key)
        val fullChain = catalogParents + chain

        return fullChain.joinToString("\n") {
            "- ${it.name} (depth: ${it.depth}): ${it.coverageArea}"
        }
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
            .replace("{{parentChain}}", parentChain.ifBlank { "None" })
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
                parentTopicKey = parentKey,
                coverageArea = it["coverageArea"] as String,
                depth = depth,
                leaf = it["leaf"] as? Boolean ?: false
            )
        }
    }
}
