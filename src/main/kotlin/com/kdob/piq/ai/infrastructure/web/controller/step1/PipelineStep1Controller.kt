package com.kdob.piq.ai.infrastructure.web

import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/pipeline")
class PipelineStep1Controller(
//    private val step1QuestionGenerationService: Step1QuestionGenerationService,
    private val artifactStorage: ArtifactStorage
) {

//    @PostMapping("/{pipelineId}/step-1")
//    fun runStep1(@PathVariable pipelineId: Long): Map<String, String> {
//
//        val step0Yaml = artifactStorage.loadStep0Artifact(pipelineId)
//
//        step1QuestionGenerationService.generate(
//            pipelineId = pipelineId,
//            step0Yaml = step0Yaml
//        )
//
//        return mapOf(
//            "pipelineId" to pipelineId.toString(),
//            "status" to "WAITING_FOR_APPROVAL"
//        )
//    }
}