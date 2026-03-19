package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.infrastructure.web.dto.PipelineArtifactUpdateRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.facade.ArtifactFacade
import com.kdob.piq.ai.infrastructure.web.validation.PipelineName
import com.kdob.piq.ai.infrastructure.web.validation.StepNumber
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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