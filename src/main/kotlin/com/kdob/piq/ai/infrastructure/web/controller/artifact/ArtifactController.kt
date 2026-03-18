package com.kdob.piq.ai.infrastructure.web.controller.artifact

import com.kdob.piq.ai.infrastructure.web.dto.PipelineArtifactUpdateRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.facade.ArtifactFacade
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import com.kdob.piq.ai.infrastructure.web.validation.StepNumber
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pipelines")
@Validated
class ArtifactController(
    private val artifactFacade: ArtifactFacade
) {
    @GetMapping("/{pipelineName}/artifacts/{step}")
    fun get(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable @StepNumber step: Int
    ): String {
        return artifactFacade.get(pipelineName, step)
    }

    @PutMapping("/{pipelineName}/artifacts/{step}")
    fun update(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable @StepNumber step: Int,
        @RequestBody request: PipelineArtifactUpdateRequest
    ): PipelineResponse {
        return artifactFacade.update(pipelineName, step, request.content, request.status)
    }

    @DeleteMapping("/{pipelineName}/artifacts/{step}")
    fun remove(
        @PathVariable @PipelineName pipelineName: String,
        @PathVariable @StepNumber step: Int
    ): PipelineResponse {
        return artifactFacade.remove(pipelineName, step)
    }
}