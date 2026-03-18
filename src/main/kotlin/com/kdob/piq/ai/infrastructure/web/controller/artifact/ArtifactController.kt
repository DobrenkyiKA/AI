package com.kdob.piq.ai.infrastructure.web.controller.artifact

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.web.dto.PipelineArtifactUpdateRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.facade.ArtifactFacade
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pipeline")
@Validated
class ArtifactController(
    private val pipelineService: PipelineService,
    private val artifactFacade: ArtifactFacade
) {
    @GetMapping("/{pipelineName}/artifact/{step}")
    fun getArtifactByStep(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable step: Int
    ): String {
        return artifactFacade.get(pipelineName, step)
    }

    @PutMapping("/{pipelineName}/artifact/{step}")
    fun updateArtifactByStep(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable step: Int,
        @RequestBody request: PipelineArtifactUpdateRequest
    ): PipelineResponse {
        return artifactFacade.update(pipelineName, step, request.content, request.status)
    }

    @PostMapping("/{pipelineName}/publish")
    fun publishArtifact(@PathVariable @PipelineName pipelineName: String): PipelineResponse {
        artifactFacade.publish(pipelineName)
        return pipelineService.get(pipelineName).toResponse()
    }

    @DeleteMapping("/{pipelineName}/artifact/{step}")
    fun removeArtifact(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable step: Int
    ): PipelineResponse {
        return artifactFacade.remove(pipelineName, step)
    }
}