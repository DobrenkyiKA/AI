package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.step0.Step0TopicIntakeService
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/pipeline")
class PipelineController(
    private val intakeService: Step0TopicIntakeService
) {
    @GetMapping
    fun getAllPipelines(): List<PipelineResponse> {
        return intakeService.findAll().map { it.toResponse() }
    }

    @GetMapping("/{pipelineName}")
    fun getPipeline(@PathVariable pipelineName: String): PipelineResponse {
        return intakeService.findByName(pipelineName)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline not found")
    }

    @GetMapping("/{pipelineName}/artifact")
    fun getPipelineArtifact(@PathVariable pipelineName: String): String {
        return intakeService.getPipelineArtifact(pipelineName)
    }

    @PutMapping("/{pipelineName}")
    fun updatePipeline(
        @PathVariable pipelineName: String,
        @RequestBody yaml: String
    ): PipelineResponse {
        return intakeService.updatePipeline(pipelineName, yaml).toResponse()
    }

    @PostMapping
    fun createPipeline(@RequestBody yaml: String): ResponseEntity<PipelineResponse> {
        val created = intakeService.intake(yaml)
        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{name}")
            .buildAndExpand(created.name)
            .toUri()

        return ResponseEntity.created(location).body(created.toResponse())
    }

    @DeleteMapping("/{pipelineName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePipeline(@PathVariable pipelineName: String) {
        intakeService.deletePipeline(pipelineName)
    }

    private fun PipelineEntity.toResponse() = PipelineResponse(
        pipelineName = name,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}