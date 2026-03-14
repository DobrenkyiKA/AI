package com.kdob.piq.ai.application.service.topictree

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TopicTreeReviewStepService(
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    private val logger = LoggerFactory.getLogger(TopicTreeReviewStepService::class.java)

    override fun getStepType(): String = "TOPIC_TREE_REVIEW"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline

        val topicTreeStep = pipeline.steps.find { it.stepType == "TOPIC_TREE_GENERATION" }
            ?: throw IllegalStateException("TOPIC_TREE_GENERATION step not found in pipeline '${pipeline.name}'")

        val topicTreeArtifact = topicTreeStep.artifact as? TopicTreeArtifactEntity
            ?: throw IllegalStateException("Topic tree artifact not found in pipeline '${pipeline.name}'. Run TOPIC_TREE_GENERATION first.")

        when (topicTreeArtifact.status) {
            ArtifactStatus.APPROVED -> {
                logger.info(
                    "Topic tree review passed for pipeline '{}': {} topics approved",
                    pipeline.name, topicTreeArtifact.nodes.size
                )
                updatePipeline(pipeline, PipelineStatus.TOPIC_TREE_APPROVED)
            }
            ArtifactStatus.PENDING_FOR_APPROVAL -> {
                throw IllegalStateException(
                    "Topic tree artifact is pending approval in pipeline '${pipeline.name}'. " +
                        "Please review and approve the topic tree before proceeding."
                )
            }
            ArtifactStatus.TO_BE_REGENERATED -> {
                throw IllegalStateException(
                    "Topic tree artifact is marked for regeneration in pipeline '${pipeline.name}'. " +
                        "Please re-run TOPIC_TREE_GENERATION step first."
                )
            }
        }
    }
}
