package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.ParentChainService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.domain.model.TopicTreeNode
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNode
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNodeEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

private const val DEFAULT_MAX_DEPTH = 12

@Service
class TopicTreeGenerationPipelineStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService,
    private val questionCatalogClient: QuestionCatalogClient,
    private val parentChainService: ParentChainService
) : AbstractPipelineStepService(
    pipelineService, artifactStorage, pipelineStatusService,
    transactionManager, loggerService, generator, pipelineArtifactStatusService
) {

    override fun getStepType(): StepType = StepType.TOPIC_TREE_GENERATION

    override fun findNext(step: PipelineStepEntity): TopicTreeNode? {
        return transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as TopicTreeArtifactEntity
            val allNodes = artifact.nodes.toList()
            val parentKeysWithChildren = allNodes.mapNotNull { it.parentTopicKey }.toSet()

            allNodes.find {
                !it.leaf && it.depth < artifact.maxDepth && it.key !in parentKeysWithChildren
            }?.toTopicTreeNode()
        }
    }

    override fun processItem(step: PipelineStepEntity, item: Any) {
        val node = item as TopicTreeNode

        val (systemPrompt, userPrompt, maxDepth) = transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as TopicTreeArtifactEntity
            val allNodes = artifact.nodes.map { it.toTopicTreeNode() }
            val rootNode = allNodes.find { it.parentTopicKey == null }!!

            val parentChain = parentChainService.buildParentChainForTopicTree(node, allNodes, rootNode)
            val siblingTopics = allNodes
                .filter { it.parentTopicKey == node.parentTopicKey && it.key != node.key }
                .joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }

            val sys = interpolate(currentStep.systemPrompt?.content ?: "", node, parentChain, siblingTopics, artifact.maxDepth)
            val usr = interpolate(currentStep.userPrompt?.content ?: "", node, parentChain, siblingTopics, artifact.maxDepth)
            Triple(sys, usr, artifact.maxDepth)
        }

        loggerService.log(step, "Generating subtopics for: ${node.name} (depth: ${node.depth})")
        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val subtopics = parseTopicTreeNodes(rawOutput, node.key, node.depth + 1)

        val yamlContent = transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as TopicTreeArtifactEntity
            val existingKeys = artifact.nodes.map { it.key }.toSet()
            val validSubtopics = subtopics.filter { it.key !in existingKeys && it.key != node.key }

            if (validSubtopics.isEmpty()) {
                val nodeInDb = artifact.nodes.find { it.key == node.key }!!
                nodeInDb.leaf = true
                loggerService.log(currentStep, "No NEW subtopics found for ${node.name}. Marked as leaf.")
            } else {
                for (sub in validSubtopics) {
                    val leaf = sub.leaf || sub.depth >= maxDepth
                    artifact.nodes.add(sub.copy(leaf = leaf).toTopicTreeNodeEntity(artifact))
                }
                loggerService.log(currentStep, "Generated ${validSubtopics.size} subtopics for ${node.name}.")
            }

            pipelineService.saveAndFlush(pipeline)
            prepareIncrementalYaml(currentStep)
        }

        saveArtifactYaml(step, yamlContent)
    }

    override fun initializeArtifactInternal(pipelineStep: PipelineStepEntity) {
        transactionTemplate.execute {
            val topicDetail = questionCatalogClient.findTopic(pipelineStep.pipeline.topicKey)!!
            val artifact = TopicTreeArtifactEntity(pipeline = pipelineStep.pipeline, maxDepth = DEFAULT_MAX_DEPTH)
            pipelineArtifactStatusService.toInProgress(pipelineStep)

            val rootNode = TopicTreeNode(
                key = topicDetail.key,
                name = topicDetail.name,
                parentTopicKey = null,
                coverageArea = topicDetail.coverageArea,
                depth = 0,
                leaf = false
            )
            artifact.nodes.add(rootNode.toTopicTreeNodeEntity(artifact))
            pipelineStep.artifact = artifact

            pipelineService.saveAndFlush(pipelineStep.pipeline)
            val yamlContent = prepareIncrementalYaml(pipelineStep)
            saveArtifactYaml(pipelineStep, yamlContent)
            loggerService.log(pipelineStep, "Initialized Topic Tree with root: ${rootNode.name}")
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

        artifact.nodes.removeIf { it.key !in incomingKeys }

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

        saveArtifactYaml(step, yamlContent.trim())
    }

    private fun prepareIncrementalYaml(pipelineStep: PipelineStepEntity): String {
        val artifact = pipelineStep.artifact as TopicTreeArtifactEntity
        val nodes = artifact.nodes.map { it.toTopicTreeNode() }.sortedWith(compareBy({ it.depth }, { it.key }))
        return yamlMapper.writeValueAsString(
            mapOf(
                "rootTopicKey" to pipelineStep.pipeline.topicKey,
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
        ).trim()
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
