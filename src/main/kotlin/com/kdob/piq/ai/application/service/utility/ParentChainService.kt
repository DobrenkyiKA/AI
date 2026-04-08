package com.kdob.piq.ai.application.service.utility

import com.kdob.piq.ai.domain.model.TopicTreeNode
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.artifact.answer.TopicQAEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.artifact.topic.TopicTreeNodeEntity
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

data class CatalogTopicInfo(val key: String, val name: String)

@Service
class ParentChainService(
    private val questionCatalogClient: QuestionCatalogClient
) {
    private val catalogChainCache = ConcurrentHashMap<String, List<CatalogTopicInfo>>()

    fun getCatalogParentChain(rootKey: String): List<CatalogTopicInfo> {
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

    fun buildParentChainForTopicTree(
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
            curr = if (parentKey != null) allNodes.find { it.key == parentKey } else null
        }

        chain.removeIf { it.key == current.key }

        val catalogParents = getCatalogParentChain(rootNode.key)
        val catalogNodes = catalogParents.map { info ->
            TopicTreeNode(
                key = info.key,
                name = info.name,
                coverageArea = "",
                depth = 0,
                leaf = false,
                parentTopicKey = null
            )
        }
        val fullChain = catalogNodes + chain

        return fullChain.joinToString("\n") {
            "- ${it.name} (depth: ${it.depth}): ${it.coverageArea}"
        }
    }

    fun buildTopicParentsChain(
        node: TopicTreeNodeEntity,
        allNodes: Collection<TopicTreeNodeEntity>,
        pipelineRootKey: String
    ): String {
        return buildTopicParentsChain(node.key, allNodes, pipelineRootKey)
    }

    fun buildTopicParentsChain(
        topicKey: String,
        allNodes: Collection<Any>,
        pipelineRootKey: String
    ): String {
        val chain = mutableListOf<String>()
        var currentKey: String? = topicKey

        while (currentKey != null) {
            val node = allNodes.find {
                when (it) {
                    is TopicTreeNodeEntity -> it.key == currentKey
                    is TopicQAEntity -> it.key == currentKey
                    else -> false
                }
            } ?: break

            val parentKey = when (node) {
                is TopicTreeNodeEntity -> node.parentTopicKey
                is TopicQAEntity -> {
                    // TopicQAEntity doesn't store parentTopicKey directly, 
                    // we'd need to find it from the TopicTree if we want the full chain 
                    // from within this structure. 
                    // But usually parentChain is already set in TopicQAEntity.
                    null 
                }
                else -> null
            }

            if (currentKey != topicKey) {
                val name = when (node) {
                    is TopicTreeNodeEntity -> node.name
                    is TopicQAEntity -> node.name
                    else -> ""
                }
                if (name.isNotEmpty()) chain.add(0, name)
            }
            currentKey = parentKey
        }

        val catalogNames = getCatalogParentChain(pipelineRootKey).map { it.name }
        return (catalogNames + chain).joinToString(" > ")
    }
}
