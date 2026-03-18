package com.kdob.piq.ai.infrastructure.web.mapper

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepResponse

object PipelineMapper {
    fun PipelineEntity.toResponse(): PipelineResponse {
//        val logs = generationLogRepository.findByPipelineNameOrderByCreatedAtAsc(name)
//            .map { GenerationLogResponse(it.message, it.stepOrder, it.createdAt) }

        return PipelineResponse(
            pipelineName = name,
            topicKey = topicKey,
            status = status.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
//            logs = logs,
            steps = steps.mapIndexed { index, step ->
                PipelineStepResponse(
                    step = index,
                    type = step.stepType,
                    status = step.artifact?.status,
                    systemPromptName = step.systemPrompt?.name,
                    systemPrompt = step.systemPrompt?.content ?: "",
                    userPromptName = step.userPrompt?.name,
                    userPrompt = step.userPrompt?.content ?: ""
                )
            }
        )
    }
}