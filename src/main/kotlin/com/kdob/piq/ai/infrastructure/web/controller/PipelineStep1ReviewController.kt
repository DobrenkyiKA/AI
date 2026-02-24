package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.infrastructure.web.dto.Step1ResultResponse
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.yaml.snakeyaml.Yaml
import java.util.UUID

@RestController
@RequestMapping("/pipeline")
class PipelineStep1ReviewController(
    private val artifactStorage: ArtifactStorage,
    private val pipelineRepository: PipelineRepository
) {

    @GetMapping("/{pipelineId}/step-1")
    fun getStep1Result(
        @PathVariable pipelineId: UUID
    ): Step1ResultResponse {

        val yaml = Yaml()
        val parsed = yaml.load<Map<String, Any>>(
            artifactStorage.loadStep1Questions(pipelineId)
        )

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
    fun approveStep1(@PathVariable pipelineId: UUID) {
        pipelineRepository.updateStatus(pipelineId, PipelineStatus.APPROVED)
    }
}