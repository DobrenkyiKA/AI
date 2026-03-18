package com.kdob.piq.ai.infrastructure.web.facade

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PipelineFacade(
    private val pipelineService: PipelineService
) {
    @Transactional(readOnly = true)
    fun get(name: String): PipelineResponse = pipelineService.get(name).toResponse()

    @Transactional(readOnly = true)
    fun findAll(): List<PipelineResponse> = pipelineService.getAll().map { it.toResponse() }

    fun delete(name: String) = pipelineService.deletePipeline(name)

    fun create(name: String, topicKey: String, steps: List<CreatePipelineStepRequest>) =
        pipelineService.create(name, topicKey, steps).toResponse()

    fun abort(pipelineName: String) {
        pipelineService.abort(pipelineName)
    }

    fun pause(pipelineName: String) {
        pipelineService.pause(pipelineName)
    }

    fun runFrom(pipelineName: String, startStep: Int): PipelineResponse =
        pipelineService.runFrom(pipelineName, startStep).toResponse()
    fun updateMetadata(
        name: String,
        topicKey: String? = null,
        steps: List<UpdatePipelineStepRequest>? = null
    ): PipelineResponse {
        return pipelineService.updateMetadata(name, topicKey, steps).toResponse()
    }
}