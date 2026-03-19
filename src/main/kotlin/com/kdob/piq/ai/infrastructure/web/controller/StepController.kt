package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import com.kdob.piq.ai.infrastructure.web.validation.StepNumber
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pipelines")
@Validated
class StepController(
    private val pipelineService: PipelineService
) {

    @GetMapping("/step-types")
    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> {
        return pipelineService.getAvailableStepTypes()
    }

    @PostMapping("/{pipelineName}/run/{step}")
    fun runStep(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable @StepNumber step: Int
    ): PipelineResponse {
        pipelineService.runStep(pipelineName, step)
        return pipelineService.get(pipelineName).toResponse()
    }

}