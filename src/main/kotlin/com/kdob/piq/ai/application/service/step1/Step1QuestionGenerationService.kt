package com.kdob.piq.ai.application.service.step1

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.GeminiChat
import com.kdob.piq.ai.application.service.GenerationStep
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep1Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.Step0TopicEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.Step1TopicWithQuestionsEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Step1QuestionGenerationService(
    private val generator: GeminiChat,
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
) : GenerationStep {
    private val yamlMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).registerKotlinModule()

    override fun getStepType(): String = "QUESTIONS_GENERATION"

    @Transactional
    override fun generate(pipeline: PipelineEntity, step: PipelineStepEntity) {
        val artifactStep0 = pipeline.artifactStep0
            ?: throw IllegalStateException("Step 0 artifact not found for pipeline: ${pipeline.name}")

        if (artifactStep0.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Step 0 artifact is not APPROVED. Current status: ${artifactStep0.status}")
        }

        if (pipeline.artifactStep1 != null) {
            pipeline.artifactStep1 = null
            pipelineRepository.saveAndFlush(pipeline)
        }

        val artifactStep1 = ArtifactStep1Entity(pipeline = pipeline)

        for (topic in artifactStep0.topics) {
            val systemPrompt = interpolate(step.systemPrompt, topic)
            val userPrompt = interpolate(step.userPrompt, topic)

            val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
            val questions = parseQuestions(rawOutput)

            val topicWithQuestions = Step1TopicWithQuestionsEntity(
                key = topic.key,
                name = topic.name,
                artifactStep1 = artifactStep1
            )
            topicWithQuestions.questions.addAll(questions)
            artifactStep1.topicsWithQuestions.add(topicWithQuestions)
        }

        pipeline.artifactStep1 = artifactStep1
        pipeline.status = PipelineStatus.STEP_1_PENDING_FOR_APPROVAL
        pipeline.updatedAt = java.time.Instant.now()
        pipelineRepository.save(pipeline)

        val yamlContent = yamlMapper.writeValueAsString(
            mapOf(
                "topics" to artifactStep1.topicsWithQuestions.map {
                    mapOf(
                        "key" to it.key,
                        "name" to it.name,
                        "questions" to it.questions.toList()
                    )
                }
            ))
        artifactStorage.saveStep1Questions(pipeline.name, yamlContent.trim())
    }

    private fun interpolate(prompt: String, topic: Step0TopicEntity): String {
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