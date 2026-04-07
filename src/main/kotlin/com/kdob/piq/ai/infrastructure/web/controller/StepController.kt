package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import com.kdob.piq.ai.infrastructure.web.facade.PipelineFacade
import com.kdob.piq.ai.infrastructure.web.facade.StepFacade
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
    private val pipelineFacade: PipelineFacade,
    private val stepFacade: StepFacade
) {
    @GetMapping("/step-types")
    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> {
        return stepFacade.getAvailableStepTypes()
    }

    @PostMapping("/{pipelineName}/run/{step}")
    fun runStep(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable @StepNumber step: Int
    ): PipelineResponse {
        stepFacade.runStep(pipelineName, step)
        return pipelineFacade.get(pipelineName)
    }

    @PostMapping("/{pipelineName}/run-from/{step}")
    fun runFrom(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        return stepFacade.runFrom(pipelineName, step)
    }
}