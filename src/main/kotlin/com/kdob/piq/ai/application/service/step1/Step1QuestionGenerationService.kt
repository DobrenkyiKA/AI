package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.step1.GeminiQuestionGenerator
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep1Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.Step0TopicEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.Step1TopicWithQuestionsEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Step1QuestionGenerationService(
    private val generator: GeminiQuestionGenerator,
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage,
) {
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Transactional
    fun generate(pipelineName: String) {
        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: throw IllegalArgumentException("Pipeline not found: $pipelineName")

        val artifactStep0 = pipeline.artifactStep0 
            ?: throw IllegalStateException("Step 0 artifact not found for pipeline: $pipelineName")
        
        if (artifactStep0.status != ArtifactStatus.APPROVED) {
            throw IllegalStateException("Step 0 artifact is not APPROVED. Current status: ${artifactStep0.status}")
        }

        if (pipeline.artifactStep1 != null) {
            pipeline.artifactStep1 = null
            pipelineRepository.saveAndFlush(pipeline)
        }

        val artifactStep1 = ArtifactStep1Entity(pipeline = pipeline)
        
        for (topic in artifactStep0.topics) {
            val prompt = buildPrompt(topic)
            val rawOutput = generator.generateQuestions(prompt)
            val questions = parseQuestions(rawOutput)
            
            val topicWithQuestions = Step1TopicWithQuestionsEntity(
                key = topic.key,
                title = topic.title,
                artifactStep1 = artifactStep1
            )
            topicWithQuestions.questions.addAll(questions)
            artifactStep1.topicsWithQuestions.add(topicWithQuestions)
        }

        pipeline.artifactStep1 = artifactStep1
        pipeline.status = PipelineStatus.STEP_1_PENDING_FOR_APPROVAL
        pipeline.updatedAt = java.time.Instant.now()
        pipelineRepository.save(pipeline)

        val yamlContent = yamlMapper.writeValueAsString(mapOf(
            "pipeline" to pipelineName,
            "topics" to artifactStep1.topicsWithQuestions.map { 
                mapOf(
                    "key" to it.key,
                    "title" to it.title,
                    "questions" to it.questions.toList()
                )
            }
        ))
        artifactStorage.saveStep1Questions(pipelineName, yamlContent)
    }

    private fun buildPrompt(topic: Step0TopicEntity): String = """
You are a senior technical interviewer.

Generate exactly ${topic.constraints.questionCount} interview-grade questions for the following topic:

Topic: ${topic.title}
Description: ${topic.description}

Constraints:
- Target Audience: ${topic.constraints.targetAudience}
- Experience Level: ${topic.constraints.experienceLevel}
- Intended Usage: ${topic.constraints.intendedUsage.joinToString(", ")}
- Exclusions (DO NOT INCLUDE): ${topic.constraints.exclusions.joinToString(", ")}

Rules:
- Generate exactly ${topic.constraints.questionCount} questions.
- No answers.
- No explanations.
- Output YAML ONLY in the following format:

questions:
  - <question 1 text>
  - <question 2 text>
""".trimIndent()

    private fun parseQuestions(rawOutput: String): List<String> {
        val cleaned = rawOutput.trim().removeSurrounding("```yaml", "```").trim()
        val data = yamlMapper.readValue(cleaned, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val questions = data["questions"] as? List<String> ?: emptyList()
        return questions
    }
}