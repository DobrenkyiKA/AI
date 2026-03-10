package com.kdob.piq.ai.application.service.questions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.application.service.PipelineStepService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.QuestionsArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineTopicEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineTopicWithQuestionsEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionPipelineStepService(
    private val generator: GeminiChat,
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
) : PipelineStepService {
    private val yamlMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    override fun getStepType(): String = "QUESTIONS_GENERATION"

    @Transactional
    override fun generate(pipeline: PipelineEntity, step: PipelineStepEntity) {
        val topicsArtifact = pipeline.topicsArtifact
            ?: throw IllegalStateException("Topics artifact not found for pipeline: ${pipeline.name}")

        if (topicsArtifact.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Topics artifact is not APPROVED. Current status: ${topicsArtifact.status}")
        }

        if (pipeline.questionsArtifact != null) {
            pipeline.questionsArtifact = null
            pipelineRepository.saveAndFlush(pipeline)
        }

        val questionsArtifact = QuestionsArtifactEntity(pipeline = pipeline)

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

        pipeline.questionsArtifact = questionsArtifact
        pipeline.status = PipelineStatus.QUESTIONS_PENDING_FOR_APPROVAL
        pipeline.updatedAt = java.time.Instant.now()
        pipelineRepository.save(pipeline)

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
        artifactStorage.saveQuestionsArtifact(pipeline.name, yamlContent.trim())
    }

    private fun interpolate(prompt: String, topic: PipelineTopicEntity): String {
        return prompt
            .replace("{{topicName}}", topic.name)
            .replace("{{coverageArea}}", topic.coverageArea)
    }

    @Transactional
    fun generate(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw IllegalArgumentException("Pipeline not found: $pipelineName")

        val step = pipeline.steps.find { it.stepType == getStepType() }
            ?: throw IllegalStateException("Step ${getStepType()} not found in pipeline $pipelineName")

        generate(pipeline, step)
    }

    private fun parseQuestions(rawOutput: String): List<String> {
        val cleaned = rawOutput.trim().removeSurrounding("```yaml", "```").trim()
        val data = yamlMapper.readValue(cleaned, Map::class.java)

        @Suppress("UNCHECKED_CAST")
        val questions = data["questions"] as? List<String> ?: emptyList()
        return questions
    }
}