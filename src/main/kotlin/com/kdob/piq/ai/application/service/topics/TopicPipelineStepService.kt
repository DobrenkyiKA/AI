package com.kdob.piq.ai.application.service.topics

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TopicPipelineStepService(
    private val generator: GeminiChat,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    override fun getStepType(): String = "TOPICS_GENERATION"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline
        val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Main topic not found: ${pipeline.topicKey}")

        val systemPrompt = interpolate(step.systemPrompt?.content ?: "", topicDetail.name, topicDetail.coverageArea, topicDetail.exclusions, step)
        val userPrompt = interpolate(step.userPrompt?.content ?: "", topicDetail.name, topicDetail.coverageArea, topicDetail.exclusions, step)

        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val subTopics = parseSubTopics(rawOutput)

        // Ensure top level generated topics point to parent topic
        val topicsWithParent = subTopics.map {
            if (it.parentTopicKey == null) it.copy(parentTopicKey = pipeline.topicKey) else it
        }

        saveTopicsArtifact(pipeline, step, topicsWithParent)
    }

    private fun interpolate(prompt: String, topicName: String, coverageArea: String, exclusions: String, step: PipelineStepEntity): String {
        val result = interpolateCommon(prompt, step.pipeline, topicName, coverageArea)
        return handleExclusions(result, exclusions)
    }
}