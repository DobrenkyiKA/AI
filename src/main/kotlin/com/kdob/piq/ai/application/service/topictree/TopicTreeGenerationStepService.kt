package com.kdob.piq.ai.application.service.topictree

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.TopicTreeNode
import com.kdob.piq.ai.domain.repository.GenerationLogRepository
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNode
import com.kdob.piq.ai.infrastructure.persistence.mapping.toTopicTreeNodeEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class TopicTreeGenerationStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient,
    private val generationLogRepository: GenerationLogRepository,
    transactionManager: PlatformTransactionManager
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(TopicTreeGenerationStepService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    companion object {
        const val DEFAULT_MAX_DEPTH = 6
    }

    override fun getStepType(): String = "TOPIC_TREE_GENERATION"

    override fun generate(step: PipelineStepEntity) {
        val pipelineId = step.pipeline.id!!

        var artifact = transactionTemplate.execute {
            val p = pipelineRepository.findById(pipelineId)!!
            val s: PipelineStepEntity = p.steps.find { it.id == step.id }!!
            s.artifact as? TopicTreeArtifactEntity
        }

        if (artifact == null) {
            log(pipelineId, "Starting new Topic Tree Generation...")
            artifact = initializeArtifact(pipelineId, step.id!!)
        } else {
            log(pipelineId, "Resuming Topic Tree Generation...")
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

            val nodeToExpand = findNodeToExpand(pipelineId, step.id!!)
            if (nodeToExpand == null) {
                log(pipelineId, "Topic Tree Generation completed successfully.")
                finalizeArtifact(pipelineId, step.id!!)
                return
            }

            try {
                expandNode(pipelineId, step.id!!, nodeToExpand)
            } catch (e: Exception) {
                log(pipelineId, "Error during expansion of ${nodeToExpand.name}: ${e.message}")
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
        
        artifact.nodes.clear()
        for (it in topicsList) {
            val node = TopicTreeNode(
                key = it["key"] as String,
                name = it["name"] as String,
                parentTopicKey = it["parentTopicKey"] as? String,
                coverageArea = it["coverageArea"] as String,
                depth = (it["depth"] as Number).toInt(),
                leaf = it["leaf"] as? Boolean ?: false
            )
            artifact.nodes.add(node.toTopicTreeNodeEntity(artifact))
        }
        
        artifactStorage.saveTopicTreeArtifact(step.pipeline.topicKey, step.pipeline.name, yamlContent.trim())
    }

    private fun initializeArtifact(pipelineId: Long, stepId: Long): TopicTreeArtifactEntity {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            
            val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
                ?: throw IllegalStateException("Root topic not found: ${pipeline.topicKey}")

            val artifact = TopicTreeArtifactEntity(pipeline = pipeline, maxDepth = DEFAULT_MAX_DEPTH)
            artifact.status = ArtifactStatus.PENDING_FOR_APPROVAL
            
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
            saveIncrementalYaml(pipeline, artifact)
            log(pipelineId, "Initialized Topic Tree with root: ${rootNode.name}")
            artifact
        }!!
    }

    private fun findNodeToExpand(pipelineId: Long, stepId: Long): TopicTreeNode? {
        return transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as TopicTreeArtifactEntity
            val allNodes = artifact.nodes.toList()
            val parentKeysWithChildren = allNodes.mapNotNull { it.parentTopicKey }.toSet()
            
            val candidate = allNodes.find { 
                !it.leaf && it.depth < DEFAULT_MAX_DEPTH && it.key !in parentKeysWithChildren 
            }
            candidate?.toTopicTreeNode()
        }
    }

    private fun expandNode(pipelineId: Long, stepId: Long, node: TopicTreeNode) {
        val (systemPrompt, userPrompt) = transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as TopicTreeArtifactEntity
            val allNodes = artifact.nodes.map { it.toTopicTreeNode() }
            val rootNode = allNodes.find { it.parentTopicKey == null }!!
            
            val parentChain = buildParentChain(node, allNodes, rootNode)
            val siblingTopics = allNodes
                .filter { it.parentTopicKey == node.parentTopicKey && it.key != node.key }
                .joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }

            val sys = interpolate(step.systemPrompt?.content ?: "", node, parentChain, siblingTopics)
            val usr = interpolate(step.userPrompt?.content ?: "", node, parentChain, siblingTopics)
            Pair(sys, usr)
        }!!

        log(pipelineId, "Generating subtopics for: ${node.name} (depth: ${node.depth})")
        
        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val subtopics = parseTopicTreeNodes(rawOutput, node.key, node.depth + 1)

        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            val step: PipelineStepEntity = pipeline.steps.find { it.id == stepId }!!
            val artifact = step.artifact as TopicTreeArtifactEntity
            
            if (subtopics.isEmpty()) {
                val nodeInDb = artifact.nodes.find { it.key == node.key }!!
                nodeInDb.leaf = true
                log(pipelineId, "No subtopics found for ${node.name}. Marked as leaf.")
            } else {
                for (sub in subtopics) {
                    val leaf = sub.leaf || sub.depth >= DEFAULT_MAX_DEPTH
                    artifact.nodes.add(sub.copy(leaf = leaf).toTopicTreeNodeEntity(artifact))
                }
                log(pipelineId, "Generated ${subtopics.size} subtopics for ${node.name}.")
            }
            
            pipelineRepository.saveAndFlush(pipeline)
            saveIncrementalYaml(pipeline, artifact)
        }
    }

    private fun finalizeArtifact(pipelineId: Long, stepId: Long) {
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            updatePipeline(pipeline, PipelineStatus.WAITING_ARTIFACT_APPROVAL)
        }
    }

    private fun log(pipelineId: Long, message: String) {
        logger.info("[Pipeline $pipelineId] $message")
        transactionTemplate.execute {
            val pipeline: PipelineEntity = pipelineRepository.findById(pipelineId)!!
            generationLogRepository.save(GenerationLogEntity(pipeline, message))
        }
    }

    private fun saveIncrementalYaml(pipeline: PipelineEntity, artifact: TopicTreeArtifactEntity) {
        val nodes = artifact.nodes.map { it.toTopicTreeNode() }.sortedWith(compareBy({ it.depth }, { it.key }))
        val yamlData = mapOf(
            "rootTopicKey" to pipeline.topicKey,
            "totalTopics" to nodes.size,
            "maxDepth" to DEFAULT_MAX_DEPTH,
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
        val yamlContent = yamlMapper.writeValueAsString(yamlData)
        artifactStorage.saveTopicTreeArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())
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
        siblingTopics: String
    ): String {
        return prompt
            .replace("{{topicKey}}", topic.key)
            .replace("{{topicName}}", topic.name)
            .replace("{{coverageArea}}", topic.coverageArea)
            .replace("{{parentChain}}", parentChain)
            .replace("{{siblingTopics}}", siblingTopics.ifBlank { "None" })
            .replace("{{depth}}", topic.depth.toString())
            .replace("{{maxDepth}}", DEFAULT_MAX_DEPTH.toString())
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
}
