package com.kdob.piq.ai.api.controller

import com.kdob.piq.ai.application.service.Step0TopicIntakeService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pipeline")
class PipelineStep0Controller(
    private val intakeService: Step0TopicIntakeService
) {

    @PostMapping("/step-0")
    fun createPipeline(
        @RequestBody yaml: String
    ): Map<String, String> {

        val pipeline = intakeService.intake(yaml)

        return mapOf(
            "pipelineId" to pipeline.id.toString(),
            "status" to pipeline.status.name
        )
    }
}