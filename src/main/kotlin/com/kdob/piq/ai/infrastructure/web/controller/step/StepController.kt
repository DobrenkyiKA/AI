package com.kdob.piq.ai.infrastructure.web.controller.step

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pipeline")
class StepController(
    private val pipelineService: PipelineService
) {

    @GetMapping("/step-types")
    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> {
        return pipelineService.getAvailableStepTypes()
    }

    @PostMapping("/{pipelineName}/run/{step}")
    fun runStep(
        @PathVariable pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        pipelineService.runStep(pipelineName, step)
        return pipelineService.get(pipelineName).toResponse()
    }

}