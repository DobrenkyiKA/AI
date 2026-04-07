package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.infrastructure.persistence.GenerationLogRepository
import com.kdob.piq.ai.infrastructure.web.dto.GenerationLogResponse
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pipelines/{pipelineName}/logs")
@Validated
class GenerationLogController(
    private val generationLogRepository: GenerationLogRepository
) {
    @GetMapping
    fun getLogs(
        @PathVariable @PipelineName pipelineName: String,
        @RequestParam step: Int
    ): List<GenerationLogResponse> {
        val logs = generationLogRepository.findByPipelineNameAndStepOrderInOrderByCreatedAtAsc(pipelineName, listOf(step, null))
        return logs.map { GenerationLogResponse(it.message, it.stepOrder, it.createdAt) }
    }
}
