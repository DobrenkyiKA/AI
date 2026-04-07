package com.kdob.piq.ai.application.service.step

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.application.service.ai.GoogleAiChatService
import com.kdob.piq.ai.application.service.utility.LoggerService
import com.kdob.piq.ai.application.service.utility.ParentChainService
import com.kdob.piq.ai.application.service.utility.PipelineArtifactStatusService
import com.kdob.piq.ai.application.service.utility.PipelineStatusService
import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.infrastructure.persistence.entity.*
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager

@Service
class QuestionsGenerationPipelineStepService(
    pipelineService: PipelineService,
    artifactStorage: ArtifactStorage,
    pipelineStatusService: PipelineStatusService,
    transactionManager: PlatformTransactionManager,
    loggerService: LoggerService,
    generator: GoogleAiChatService,
    pipelineArtifactStatusService: PipelineArtifactStatusService,
    private val parentChainService: ParentChainService
) : AbstractQAStepService(
    pipelineService, artifactStorage, pipelineStatusService,
    transactionManager, loggerService, generator, pipelineArtifactStatusService
) {

    override fun getStepType(): StepType = StepType.QUESTIONS_GENERATION

    override fun previousStepType(): StepType = StepType.TOPIC_TREE_GENERATION

    override fun totalCountLabel(): String = "totalQuestions"

    override fun entryToYamlMap(entry: QAEntryEntity): Map<String, Any?> = mapOf(
        "text" to entry.questionText,
        "level" to entry.level
    )

    override fun yamlToEntry(q: Map<String, Any>, topicQA: TopicQAEntity): QAEntryEntity = QAEntryEntity(
        questionText = q["text"] as String,
        level = q["level"] as String,
        topicQA = topicQA
    )

    override fun findNext(step: PipelineStepEntity): TopicTreeNodeEntity? {
        loggerService.log(step, "Finding next topic to generate questions for...")
        return transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as AnswersArtifactEntity

            val topicTreeStep = pipeline.steps.find { it.stepType == StepType.TOPIC_TREE_GENERATION }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val generatedTopicKeys = artifact.topicsWithQA.map { it.key }.toSet()
            topicTreeArtifact.nodes.find { it.key !in generatedTopicKeys }
        }?.also {
            loggerService.log(step, "Found next topic: ${it.name}")
        }
    }

    override fun processItem(step: PipelineStepEntity, item: Any) {
        val node = item as TopicTreeNodeEntity

        val (systemPrompt, userPrompt, parentChain) = transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val topicTreeStep = pipeline.steps.find { it.stepType == StepType.TOPIC_TREE_GENERATION }!!
            val topicTreeArtifact = topicTreeStep.artifact as TopicTreeArtifactEntity

            val nodesByParent = topicTreeArtifact.nodes.groupBy { it.parentTopicKey }
            val children = nodesByParent[node.key] ?: emptyList()
            val chain = parentChainService.buildParentChainForQuestions(node, topicTreeArtifact.nodes, pipeline.topicKey)

            val sys = interpolateQuestionPrompt(currentStep.systemPrompt?.content ?: "", node, children, chain)
            val usr = interpolateQuestionPrompt(currentStep.userPrompt?.content ?: "", node, children, chain)
            Triple(sys, usr, chain)
        }

        loggerService.log(step, "Generating questions for topic: ${node.name} (leaf: ${node.leaf})")
        loggerService.log(step, "Calling AI service...")
        val rawOutput = generator.executePrompt(systemPrompt, userPrompt)
        loggerService.log(step, "AI service response received.")
        val questions = parseQuestionsWithLevels(rawOutput)

        val yamlContent = transactionTemplate.execute {
            val pipeline = pipelineService.get(step.pipeline.name)
            val currentStep = pipeline.steps.find { it.id == step.id }!!
            val artifact = currentStep.artifact as AnswersArtifactEntity

            if (questions.isNotEmpty()) {
                val topicQA = TopicQAEntity(
                    key = node.key,
                    name = node.name,
                    answersArtifact = artifact
                ).apply { this.parentChain = parentChain }
                topicQA.entries.addAll(questions.map { (text, level) ->
                    QAEntryEntity(questionText = text, level = level, topicQA = topicQA)
                })
                artifact.topicsWithQA.add(topicQA)
            }

            loggerService.log(currentStep, "Saving pipeline state to database...")
            pipelineService.saveAndFlush(pipeline)
            loggerService.log(currentStep, "Pipeline state saved.")
            prepareIncrementalYaml(artifact)
        }

        saveArtifactYaml(step, yamlContent)
        loggerService.log(step, "Saved ${questions.size} questions for topic: ${node.name}")
    }

    private fun interpolateQuestionPrompt(
        prompt: String,
        node: TopicTreeNodeEntity,
        children: List<TopicTreeNodeEntity>,
        parentChain: String
    ): String {
        val topicType = if (node.leaf) "leaf" else "branch"
        val childTopicsList = if (children.isNotEmpty()) {
            children.joinToString("\n") { "- ${it.name}: ${it.coverageArea}" }
        } else {
            "NONE_MARKER"
        }

        var result = prompt
            .replace("{{topicKey}}", node.key)
            .replace("{{topicName}}", node.name)
            .replace("{{coverageArea}}", node.coverageArea)
            .replace("{{topicType}}", topicType)
            .replace("{{childTopicsList}}", childTopicsList)
            .replace("{{parentChain}}", parentChain.ifBlank { "Root" })

        if (childTopicsList == "NONE_MARKER") {
            val headerAndMarkerRegex = Regex(
                """Subtopics \(for branch topics, generate cross-cutting questions\):\s*NONE_MARKER\s*""",
                RegexOption.IGNORE_CASE
            )
            result = if (headerAndMarkerRegex.containsMatchIn(result)) {
                result.replace(headerAndMarkerRegex, "")
            } else {
                result.replace("NONE_MARKER", "")
            }
        }

        return result.trim()
    }

    private fun parseQuestionsWithLevels(rawOutput: String): List<Pair<String, String>> {
        val data = parseYaml(rawOutput)

        @Suppress("UNCHECKED_CAST")
        val questionsList = data["questions"] as? List<Any> ?: emptyList()

        return questionsList.mapNotNull { item ->
            when (item) {
                is Map<*, *> -> {
                    val text = item["text"] as? String ?: return@mapNotNull null
                    val level = item["level"] as? String ?: "mid"
                    text to level
                }
                is String -> item to "mid"
                else -> null
            }
        }
    }
}
