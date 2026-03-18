package com.kdob.piq.ai.infrastructure.web.controller.pipeline

import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineRequest
import com.kdob.piq.ai.infrastructure.web.facade.PipelineFacade
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/pipelines")
@Validated
class PipelineController(
    private val pipelineFacade: PipelineFacade
) {
    @DeleteMapping("/{pipelineName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable @PipelineName pipelineName: String) {
        pipelineFacade.delete(pipelineName)
    }

    @PostMapping
    fun create(@RequestBody @Valid request: CreatePipelineRequest): ResponseEntity<PipelineResponse> {
        val created = pipelineFacade.create(request.name, request.topicKey, request.steps)
        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{pipelineName}")
            .buildAndExpand(created.pipelineName)
            .toUri()

        return ResponseEntity.created(location).body(created)
    }

    @GetMapping("/{pipelineName}")
    fun get(@PathVariable @PipelineName pipelineName: String): PipelineResponse {
        return pipelineFacade.get(pipelineName)
    }

    @GetMapping
    fun getAll(): List<PipelineResponse> {
        return pipelineFacade.getAll()
    }

    @PatchMapping("/{pipelineName}")
    fun updateMetadata(
        @PathVariable @PipelineName pipelineName: String,
        @RequestBody request: UpdatePipelineRequest
    ): PipelineResponse {
        return pipelineFacade.updateMetadata(pipelineName, request.topicKey, request.steps)
    }

    @PostMapping("/{pipelineName}/run-from/{step}")
    fun runFrom(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        return pipelineFacade.runFrom(pipelineName, step)
    }

    @PostMapping("/{pipelineName}/pause")
    fun pausePipeline(@PathVariable @PipelineName pipelineName: String): PipelineResponse {
        return pipelineFacade.pause(pipelineName)
    }

    @PostMapping("/{pipelineName}/abort")
    fun abortPipeline(@PathVariable @PipelineName pipelineName: String): PipelineResponse {
        return pipelineFacade.abort(pipelineName)
    }
}