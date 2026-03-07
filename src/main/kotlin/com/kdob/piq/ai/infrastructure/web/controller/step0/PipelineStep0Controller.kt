package com.kdob.piq.ai.infrastructure.web.controller.step0

import com.kdob.piq.ai.application.service.step0.Step0TopicIntakeService
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pipeline")
class PipelineStep0Controller(
    private val intakeService: Step0TopicIntakeService
) {

    @GetMapping
    fun getAllPipelines(): List<PipelineResponse> {
        return intakeService.findAll().map {
            PipelineResponse(
                pipelineName = it.name,
                status = it.status.name,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    @PostMapping("/step-0")
    fun createPipeline(@RequestBody yaml: String): PipelineResponse {
        val created = intakeService.intake(yaml)
        return PipelineResponse(
            pipelineName = created.name,
            status = created.status.name,
            createdAt = created.createdAt,
            updatedAt = created.updatedAt
        )
    }
}