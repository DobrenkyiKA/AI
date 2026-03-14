package com.kdob.piq.ai.application.service.publish

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.CreateTopicClientRequest
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PublishStepService(
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(PublishStepService::class.java)

    override fun getStepType(): String = "PUBLISH"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline

        // Find and validate the short answers artifact (final artifact in the pipeline)
        val shortAnswersStep = pipeline.steps.find { it.stepType == "SHORT_ANSWERS_GENERATION" }
            ?: throw IllegalStateException("SHORT_ANSWERS_GENERATION step not found for pipeline: ${pipeline.name}")
        val shortAnswersArtifact = shortAnswersStep.artifact as? AnswersArtifactEntity
            ?: throw IllegalStateException("Short answers artifact not found for pipeline: ${pipeline.name}")

        if (shortAnswersArtifact.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Short answers artifact is not APPROVED. Current status: ${shortAnswersArtifact.status}")
        }

        // Find and validate the topic tree artifact
        val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }
            ?: throw IllegalStateException("TOPIC_TREE_GENERATION step not found for pipeline: ${pipeline.name}")
        val topicTreeArtifact = topicTreeStep.artifact as? TopicTreeArtifactEntity
            ?: throw IllegalStateException("Topic tree artifact not found for pipeline: ${pipeline.name}")

        // 1. Publish topic tree
        publishTopicTree(pipeline, topicTreeArtifact)

        // 2. Publish questions + answers (when QuestionCatalogClient supports it)
        logger.info(
            "Published topic tree for pipeline '{}'. Questions/answers publishing requires QuestionCatalogClient extension.",
            pipeline.name
        )

        updatePipeline(pipeline, PipelineStatus.PUBLISHED)
    }

    private fun publishTopicTree(pipeline: PipelineEntity, topicTreeArtifact: TopicTreeArtifactEntity) {
        val rootTopic = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Root topic not found in catalog: ${pipeline.topicKey}")

        val nodesByParent = topicTreeArtifact.nodes.groupBy { it.parentTopicKey }

        fun publishRecursive(parentKey: String, parentPath: String) {
            val children = nodesByParent[parentKey] ?: return
            for (child in children) {
                val request = CreateTopicClientRequest(
                    key = child.key,
                    name = child.name,
                    parentPath = parentPath,
                    coverageArea = child.coverageArea,
                    exclusions = ""
                )
                logger.info("Publishing topic: {} (parent: {})", child.key, parentKey)
                val response = questionCatalogClient.createTopic(request)
                publishRecursive(child.key, response.path)
            }
        }

        publishRecursive(pipeline.topicKey, rootTopic.path)

        logger.info(
            "Topic tree published for pipeline '{}': {} topics",
            pipeline.name, topicTreeArtifact.nodes.size
        )
    }
}
