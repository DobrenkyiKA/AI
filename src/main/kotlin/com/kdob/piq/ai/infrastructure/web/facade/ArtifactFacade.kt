package com.kdob.piq.ai.infrastructure.web.facade

import com.kdob.piq.ai.application.service.ArtifactService
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ArtifactFacade(
    private val artifactService: ArtifactService
) {
    @Transactional
    fun updateArtifact(name: String, stepIndex: Int, yamlContent: String, status: ArtifactStatus): PipelineResponse {
        return artifactService.update(name, stepIndex, yamlContent, status).toResponse()
    }

    fun get(name: String, stepIndex: Int) = artifactService.get(name, stepIndex)
    fun publish(pipelineName: String) = artifactService.publish(pipelineName)

    fun update(name: String, stepIndex: Int, yamlContent: String, status: ArtifactStatus): PipelineResponse =
        artifactService.update(name, stepIndex, yamlContent, status).toResponse()

    fun remove(pipelineName: String, stepIndex: Int): PipelineResponse =
        artifactService.remove(pipelineName, stepIndex).toResponse()
}