package com.kdob.piq.ai.application.service.questions

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineTopicEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineTopicWithQuestionsEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.QuestionsPipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsPipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionPipelineStepService(
    private val generator: GeminiChat,
    pipelineRepository: PipelineRepository,
    artifactStorage: ArtifactStorage,
) : AbstractPipelineStepService(pipelineRepository, artifactStorage) {

    override fun getStepType(): String = "QUESTIONS_GENERATION"

    @Transactional
    override fun generate(step: PipelineStepEntity) {
        val pipeline = step.pipeline
        val topicsStep = pipeline.steps.find { it.stepType == "TOPICS_GENERATION" || it.stepType == "SUBTOPICS_GENERATION" }
            ?: throw IllegalStateException("Topics generation step not found for pipeline: ${pipeline.name}")
        val topicsArtifact = topicsStep.artifact as? TopicsPipelineArtifactEntity
            ?: throw IllegalStateException("Topics artifact not found for pipeline: ${pipeline.name}")

        if (topicsArtifact.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Topics artifact is not APPROVED. Current status: ${topicsArtifact.status}")
        }

        clearOldArtifact(pipeline, step)

        val questionsArtifact = QuestionsPipelineArtifactEntity(pipeline = pipeline)

        for (topic in topicsArtifact.topics) {
            val systemPrompt = interpolate(step.systemPrompt?.content ?: "", topic)
            val userPrompt = interpolate(step.userPrompt?.content ?: "", topic)

            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            val questions = parseQuestions(rawOutput)

            val topicWithQuestions = PipelineTopicWithQuestionsEntity(
                key = topic.key,
                name = topic.name,
                questionsArtifact = questionsArtifact
            )
            topicWithQuestions.questions.addAll(questions)
            questionsArtifact.topicsWithQuestions.add(topicWithQuestions)
        }

        step.artifact = questionsArtifact
        updatePipeline(pipeline, PipelineStatus.QUESTIONS_PENDING_FOR_APPROVAL)

        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "topics" to questionsArtifact.topicsWithQuestions.map {
                    mapOf(
                        "key" to it.key,
                        "name" to it.name,
                        "questions" to it.questions.toList()
                    )
                }
            ))
        artifactStorage.saveQuestionsArtifact(pipeline.topicKey, pipeline.name, yamlContent.trim())
    }

    private fun interpolate(prompt: String, topic: PipelineTopicEntity): String {
        return prompt
            .replace("{{topicName}}", topic.name)
            .replace("{{coverageArea}}", topic.coverageArea)
    }

    private fun parseQuestions(rawOutput: String): List<String> {
        val data = parseYaml(rawOutput)
        @Suppress("UNCHECKED_CAST")
        val questions = data["questions"] as? List<String> ?: emptyList()
        return questions
    }
}