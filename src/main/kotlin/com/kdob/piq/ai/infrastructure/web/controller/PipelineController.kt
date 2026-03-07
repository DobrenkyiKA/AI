package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.step0.PipelineService
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineArtifactUpdateRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/pipeline")
class PipelineController(
    private val pipelineService: PipelineService
) {
    @GetMapping
    fun getAllPipelines(): List<PipelineResponse> {
        return pipelineService.findAll().map { it.toResponse() }
    }

    @GetMapping("/{pipelineName}")
    fun getPipeline(@PathVariable pipelineName: String): PipelineResponse {
        return pipelineService.findByName(pipelineName)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline not found")
    }

    @GetMapping("/{pipelineName}/artifact")
    fun getPipelineArtifact(@PathVariable pipelineName: String): String {
        return pipelineService.getPipelineArtifact(pipelineName)
    }

    @GetMapping("/{pipelineName}/artifact/{step}")
    fun getArtifactByStep(
        @PathVariable pipelineName: String,
        @PathVariable step: Int
    ): String {
        return pipelineService.getArtifact(pipelineName, step)
    }

    @PutMapping("/{pipelineName}/artifact/{step}")
    fun updateArtifactByStep(
        @PathVariable pipelineName: String,
        @PathVariable step: Int,
        @RequestBody request: PipelineArtifactUpdateRequest
    ): PipelineResponse {
        return pipelineService.updateArtifact(pipelineName, step, request.content, request.status).toResponse()
    }

    @PutMapping("/{pipelineName}")
    fun updatePipeline(
        @PathVariable pipelineName: String,
        @RequestBody yaml: String
    ): PipelineResponse {
        return pipelineService.updatePipeline(pipelineName, yaml).toResponse()
    }

    @PostMapping
    fun createPipeline(@RequestBody yaml: String): ResponseEntity<PipelineResponse> {
        val created = pipelineService.intake(yaml)
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
        pipelineService.deletePipeline(pipelineName)
    }

    private fun PipelineEntity.toResponse() = PipelineResponse(
        pipelineName = name,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        steps = listOf(
            PipelineStepResponse(0, artifactStep0?.status),
            PipelineStepResponse(1, artifactStep1?.status)
        )
    )
}