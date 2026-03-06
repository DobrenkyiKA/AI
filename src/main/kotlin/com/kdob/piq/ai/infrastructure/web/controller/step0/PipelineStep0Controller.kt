package com.kdob.piq.ai.infrastructure.web.controller.step0

import com.kdob.piq.ai.application.service.step0.Step0TopicIntakeService
import org.springframework.http.HttpStatus
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
    ): HttpStatus {

        intakeService.intake(yaml)

        return HttpStatus.OK
    }
}