package com.kdob.piq.ai.infrastructure.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.infrastructure.web.dto.Step1ResultResponse
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/pipeline")
class PipelineStep1ReviewController(
    private val artifactStorage: ArtifactStorage,
    private val pipelineRepository: PipelineRepository
) {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @GetMapping("/{pipelineId}/step-1")
    fun getStep1Result(
        @PathVariable pipelineId: Long
    ): Step1ResultResponse {

        val parsed = yamlMapper.readValue(
            artifactStorage.loadStep1Questions(pipelineId),
            Map::class.java
        ) as Map<String, Any>

        val questions =
            parsed["questions"] as List<Map<String, String>>

        val pipeline = pipelineRepository.findById(pipelineId)
            ?: error("Pipeline not found")

        return Step1ResultResponse(
            pipelineId = pipelineId.toString(),
            status = pipeline.status.name,
            questions = questions
        )
    }

    @PostMapping("/{pipelineId}/step-1/approve")
    fun approveStep1(@PathVariable pipelineId: Long) {
        pipelineRepository.updateStatus(pipelineId, PipelineStatus.APPROVED)
    }
}