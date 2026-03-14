package com.kdob.piq.ai.application.service.topics

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.GoogleAiChatService
import com.kdob.piq.ai.application.service.OpenAiChatService
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.client.question.QuestionCatalogClient
import com.kdob.piq.ai.infrastructure.client.question.dto.TopicClientResponse
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubtopicsGenerationPipelineStepService(
    private val generator: OpenAiChatService,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
    private val questionCatalogClient: QuestionCatalogClient
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    override fun getStepType(): String = "SUBTOPICS_GENERATION"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline
        val topicDetail = questionCatalogClient.findTopic(pipeline.topicKey)
            ?: throw IllegalStateException("Main topic not found: ${pipeline.topicKey}")

        val parentKeys = topicDetail.path.split("/")
            .filter { it.isNotBlank() && it != topicDetail.key }
        
        val parents = parentKeys.mapNotNull { 
            questionCatalogClient.findTopic(it)
        }

        val systemPrompt = interpolate(step.systemPrompt?.content ?: "", topicDetail, parents, step)
        val userPrompt = interpolate(step.userPrompt?.content ?: "", topicDetail, parents, step)

        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        val subTopics = parseSubTopics(rawOutput)

        // Ensure top level generated topics point to parent topic
        val topicsWithParent = subTopics.map {
            if (it.parentTopicKey == null) it.copy(parentTopicKey = pipeline.topicKey) else it
        }

        saveTopicsArtifact(pipeline, step, topicsWithParent)
    }

    private fun interpolate(prompt: String, topicDetail: TopicClientResponse, parents: List<TopicClientResponse>, step: PipelineStepEntity): String {
        var result = interpolateCommon(prompt, step.pipeline, topicDetail.name, topicDetail.coverageArea)

        val parentContext = parents.joinToString("\n") { 
            "Parent Topic: ${it.name}\nCoverage Area: ${it.coverageArea}" 
        }
        result = result.replace("{{parentTopics}}", parentContext)

        return handleExclusions(result, topicDetail.exclusions)
    }
}
