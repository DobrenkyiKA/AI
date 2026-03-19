package com.kdob.piq.ai.infrastructure.web.facade

import com.kdob.piq.ai.application.service.StepService
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
import org.springframework.stereotype.Component

@Component
class StepFacade(
    private val stepService: StepService
) {
    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> {
        return stepService.getAvailableStepTypes()
    }
    fun runStep(pipelineName: String, stepIndex: Int) {
        stepService.runStep(pipelineName, stepIndex)
    }
    fun runFrom(pipelineName: String, startStep: Int) {
        stepService.runFrom(pipelineName, startStep)
    }
}