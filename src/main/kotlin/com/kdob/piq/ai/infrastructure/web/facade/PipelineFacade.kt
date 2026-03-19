package com.kdob.piq.ai.infrastructure.web.facade

import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.infrastructure.web.dto.CreatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.dto.PipelineResponse
import com.kdob.piq.ai.infrastructure.web.dto.UpdatePipelineStepRequest
import com.kdob.piq.ai.infrastructure.web.mapper.PipelineMapper.toResponse
import org.springframework.stereotype.Component

@Component
class PipelineFacade(
    private val pipelineService: PipelineService
) {
    fun get(name: String): PipelineResponse = pipelineService.get(name).toResponse()

    fun getAll(): List<PipelineResponse> = pipelineService.getAll().map { it.toResponse() }

    fun delete(name: String) = pipelineService.delete(name)

    fun create(name: String, topicKey: String, steps: List<CreatePipelineStepRequest>) =
        pipelineService.create(name, topicKey, steps).toResponse()

    fun abort(pipelineName: String): PipelineResponse =
        pipelineService.abort(pipelineName).toResponse()

    fun pause(pipelineName: String): PipelineResponse =
        pipelineService.pause(pipelineName).toResponse()

    fun update(
        name: String,
        steps: List<UpdatePipelineStepRequest>? = null
    ): PipelineResponse {
        return pipelineService.update(name, steps).toResponse()
    }
}