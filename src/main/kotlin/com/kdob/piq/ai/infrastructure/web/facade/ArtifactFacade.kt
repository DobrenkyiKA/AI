package com.kdob.piq.ai.infrastructure.web.facade

import com.kdob.piq.ai.application.service.ArtifactService
import com.kdob.piq.ai.infrastructure.web.dto.PipelineArtifactUpdateRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import org.springframework.stereotype.Component

@Component
class ArtifactFacade(
    private val artifactService: ArtifactService
) {
    fun get(name: String, stepIndex: Int) = artifactService.get(name, stepIndex)

    fun update(name: String, stepIndex: Int, request: PipelineArtifactUpdateRequest): PipelineResponse =
        artifactService.update(name, stepIndex, request.content, request.status).toResponse()

    fun remove(pipelineName: String, stepIndex: Int): PipelineResponse =
        artifactService.remove(pipelineName, stepIndex).toResponse()
}