package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.AbstractPipelineStepService
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.transaction.PlatformTransactionManager

abstract class AbstractQAStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService
) : AbstractPipelineStepService(
    pipelineService, artifactStorage, pipelineStatusService,
    transactionManager, loggerService, generator, pipelineArtifactStatusService
) {

    protected abstract fun previousStepType(): StepType

    protected abstract fun entryToYamlMap(entry: QAEntryEntity): Map<String, Any?>

    protected abstract fun yamlToEntry(q: Map<String, Any>, topicQA: TopicQAEntity): QAEntryEntity

    protected abstract fun totalCountLabel(): String

    override fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus) {
        val artifact = step.artifact as? AnswersArtifactEntity ?: throw IllegalStateException("Answers artifact not found")
        artifact.status = status

        val data = parseYaml(yamlContent)

        @Suppress("UNCHECKED_CAST")
        val topicsList = data["topics"] as? List<Map<String, Any>> ?: emptyList()

        val incomingByKey = topicsList.associateBy { it["key"] as String }
        artifact.topicsWithQA.removeIf { it.key !in incomingByKey }
        val existingByKey = artifact.topicsWithQA.associateBy { it.key }

        for (t in topicsList) {
            val key = t["key"] as String
            val name = t["name"] as String
            val parentChain = t["parentChain"] as? String

            @Suppress("UNCHECKED_CAST")
            val questions = t["questions"] as? List<Map<String, Any>> ?: emptyList()
            val existing = existingByKey[key]
            if (existing != null) {
                existing.parentChain = parentChain
                existing.entries.clear()
                existing.entries.addAll(questions.map { q -> yamlToEntry(q, existing) })
            } else {
                val topicQA = TopicQAEntity(
                    key = key,
                    name = name,
                    answersArtifact = artifact
                ).apply { this.parentChain = parentChain }
                topicQA.entries.addAll(questions.map { q -> yamlToEntry(q, topicQA) })
                artifact.topicsWithQA.add(topicQA)
            }
        }

        saveArtifactYaml(step, yamlContent.trim())
    }

    override fun initializeArtifactInternal(pipelineStep: PipelineStepEntity) {
        transactionTemplate.execute {
            val pipeline = pipelineService.get(pipelineStep.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == pipelineStep.id }!!

            val previousStep = pipeline.steps.find { it.stepType == previousStepType() }
                ?: throw IllegalStateException("${previousStepType().name} step not found for pipeline: ${pipeline.name}")
            
            loggerService.log(currentStep, "Checking status of previous step: [${previousStep.stepType.name}]")
            val previousArtifact = previousStep.artifact
                ?: throw IllegalStateException("Artifact not found for ${previousStepType().name} in pipeline: ${pipeline.name}")

            check(previousArtifact.status == ArtifactStatus.APPROVED) {
                "${previousStepType().name} artifact is not APPROVED. Current status: ${previousArtifact.status}"
            }
            loggerService.log(currentStep, "Previous artifact [${previousStep.stepType.name}] is APPROVED.")

            val artifact = AnswersArtifactEntity(pipeline = pipeline)
            artifact.status = ArtifactStatus.GENERATION_IN_PROGRESS
            currentStep.artifact = artifact

            loggerService.log(currentStep, "Saving initial artifact to database...")
            pipelineService.saveAndFlush(pipeline)
            loggerService.log(currentStep, "Initial artifact saved.")

            val yamlContent = prepareIncrementalYaml(artifact)
            saveArtifactYaml(currentStep, yamlContent)
            loggerService.log(currentStep, "Initialized ${getLabel()} Artifact.")
        }
    }

    protected fun prepareIncrementalYaml(artifact: AnswersArtifactEntity): String {
        val totalCount = artifact.topicsWithQA.sumOf { it.entries.size }
        return yamlMapper.writeValueAsString(
            mapOf(
                totalCountLabel() to totalCount,
                "topics" to artifact.topicsWithQA.map { topicQA ->
                    mapOf(
                        "key" to topicQA.key,
                        "name" to topicQA.name,
                        "parentChain" to topicQA.parentChain,
                        "questions" to topicQA.entries.map { entry -> entryToYamlMap(entry) }
                    )
                }
            )
        ).trim()
    }
}
