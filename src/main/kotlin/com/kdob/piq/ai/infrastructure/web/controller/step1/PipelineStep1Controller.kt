package com.kdob.piq.ai.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.Step1QuestionGenerationService
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.Step1ResultResponse
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/pipeline")
class PipelineStep1Controller(
    private val artifactStorage: ArtifactStorage,
    private val pipelineRepository: PipelineRepository,
    private val step1QuestionGenerationService: Step1QuestionGenerationService
) {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @GetMapping("/{pipelineName}/step-1")
    fun getStep1Result(@PathVariable pipelineName: String): Step1ResultResponse {

        val parsed = yamlMapper.readValue(
            artifactStorage.loadStep1Questions(pipelineName),
            Map::class.java
        )

        val questions =
            parsed["questions"] as List<Map<String, String>>

        val pipeline = pipelineRepository.findByName(pipelineName)
            ?: error("Pipeline not found")

        return Step1ResultResponse(
            pipelineName = pipelineName,
            status = pipeline.status.name,
            questions = questions
        )
    }

    @PostMapping("/{pipelineName}/step-1/approve")
    fun approveStep1(@PathVariable pipelineName: String) {
        pipelineRepository.updateStatus(pipelineName, PipelineStatus.APPROVED)
    }

    @PostMapping("/{pipelineName}/step-1")
    fun runStep1(@PathVariable pipelineName: String): Map<String, String> {
        step1QuestionGenerationService.generate(pipelineName)

        return mapOf(
            "pipelineName" to pipelineName,
            "status" to "WAITING_FOR_APPROVAL"
        )
    }
}