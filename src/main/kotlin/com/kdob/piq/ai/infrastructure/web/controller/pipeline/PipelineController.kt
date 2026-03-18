package com.kdob.piq.ai.infrastructure.web.controller.pipeline

import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineRequest
import com.kdob.piq.ai.infrastructure.web.facade.PipelineFacade
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/pipelines")
class PipelineController(
    private val pipelineFacade: PipelineFacade
) {
    @DeleteMapping("/{pipelineName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePipeline(@PathVariable pipelineName: String) {
        pipelineFacade.delete(pipelineName)
    }

    @PostMapping
    fun createPipeline(@RequestBody request: CreatePipelineRequest): ResponseEntity<PipelineResponse> {
        val created = pipelineFacade.create(request.name, request.topicKey, request.steps)
        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{name}")
            .buildAndExpand(created.pipelineName)
            .toUri()

        return ResponseEntity.created(location).body(created)
    }

    @GetMapping("/{pipelineName}")
    fun getPipeline(@PathVariable pipelineName: String): PipelineResponse {
        return pipelineFacade.get(pipelineName)
    }

    @GetMapping
    fun getAllPipelines(): List<PipelineResponse> {
        return pipelineFacade.findAll()
    }

    @PatchMapping("/{pipelineName}")
    fun updatePipelineMetadata(
        @PathVariable pipelineName: String,
        @RequestBody request: UpdatePipelineRequest
    ): PipelineResponse {
        return pipelineFacade.updateMetadata(pipelineName, request.topicKey, request.steps)
    }

    @PostMapping("/{pipelineName}/run-from/{step}")
    fun runPipelineFrom(
        @PathVariable pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        pipelineFacade.runFrom(pipelineName, step)
        return getPipeline(pipelineName)
    }

    @PostMapping("/{pipelineName}/pause")
    fun pausePipeline(@PathVariable pipelineName: String): PipelineResponse {
        pipelineFacade.pause(pipelineName)
        return getPipeline(pipelineName)
    }

    @PostMapping("/{pipelineName}/abort")
    fun abortPipeline(@PathVariable pipelineName: String): PipelineResponse {
        pipelineFacade.abort(pipelineName)
        return getPipeline(pipelineName)
    }
}