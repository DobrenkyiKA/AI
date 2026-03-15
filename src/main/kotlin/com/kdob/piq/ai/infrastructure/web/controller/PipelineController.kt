package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.web.dto.*
import com.kdob.piq.ai.infrastructure.web.dto.PipelineStepTypeResponse
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
        return pipelineService.findAll()
    }

    @GetMapping("/step-types")
    fun getAvailableStepTypes(): List<PipelineStepTypeResponse> {
        return pipelineService.getAvailableStepTypes()
    }

    @GetMapping("/{pipelineName}")
    fun getPipeline(@PathVariable pipelineName: String): PipelineResponse {
        return pipelineService.findByName(pipelineName)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline not found")
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
        return pipelineService.updateArtifact(pipelineName, step, request.content, request.status)
    }

    @PatchMapping("/{pipelineName}")
    fun updatePipelineMetadata(
        @PathVariable pipelineName: String,
        @RequestBody request: UpdatePipelineRequest
    ): PipelineResponse {
        return pipelineService.updatePipelineMetadata(pipelineName, request.topicKey, request.steps)
    }

    @PostMapping
    fun createPipeline(@RequestBody request: CreatePipelineRequest): ResponseEntity<PipelineResponse> {
        val created = pipelineService.createPipeline(request.name, request.topicKey, request.steps)
        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{name}")
            .buildAndExpand(created.pipelineName)
            .toUri()

        return ResponseEntity.created(location).body(created)
    }

    @DeleteMapping("/{pipelineName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePipeline(@PathVariable pipelineName: String) {
        pipelineService.deletePipeline(pipelineName)
    }

    @PostMapping("/{pipelineName}/run/{step}")
    fun runStep(
        @PathVariable pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        pipelineService.runStep(pipelineName, step)
        return getPipeline(pipelineName)
    }

    @PostMapping("/{pipelineName}/run-from/{step}")
    fun runPipelineFrom(
        @PathVariable pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        pipelineService.runPipelineFrom(pipelineName, step)
        return getPipeline(pipelineName)
    }

    @PostMapping("/{pipelineName}/publish")
    fun publishArtifact(@PathVariable pipelineName: String): PipelineResponse {
        pipelineService.publishArtifact(pipelineName)
        return getPipeline(pipelineName)
    }

    @DeleteMapping("/{pipelineName}/artifact/{step}")
    fun removeArtifact(
        @PathVariable pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        return pipelineService.removeArtifact(pipelineName, step)
    }

    @PostMapping("/{pipelineName}/pause")
    fun pausePipeline(@PathVariable pipelineName: String): PipelineResponse {
        pipelineService.pausePipeline(pipelineName)
        return getPipeline(pipelineName)
    }

    @PostMapping("/{pipelineName}/abort")
    fun abortPipeline(@PathVariable pipelineName: String): PipelineResponse {
        pipelineService.abortPipeline(pipelineName)
        return getPipeline(pipelineName)
    }
}
