package com.kdob.piq.ai.infrastructure.web

import com.kdob.piq.ai.application.service.Step1QuestionGenerationService
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/pipeline")
class PipelineStep1Controller(
    private val step1QuestionGenerationService: Step1QuestionGenerationService,
) {

    @PostMapping("/{pipelineName}/step-1")
    fun runStep1(@PathVariable pipelineName: String): Map<String, String> {
        step1QuestionGenerationService.generate(pipelineName)

        return mapOf(
            "pipelineName" to pipelineName,
            "status" to "WAITING_FOR_APPROVAL"
        )
    }
}