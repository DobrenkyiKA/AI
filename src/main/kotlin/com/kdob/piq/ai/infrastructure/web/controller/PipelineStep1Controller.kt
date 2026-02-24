//package com.kdob.piq.ai.infrastructure.web
//
//import com.kdob.piq.ai.application.service.Step1QuestionGenerationService
//import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
//import org.springframework.web.bind.annotation.PathVariable
//import org.springframework.web.bind.annotation.PostMapping
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RestController
//import java.util.*
//
//
//@RestController
//@RequestMapping("/pipeline")
//class PipelineStep1Controller(
//    private val service: Step1QuestionGenerationService,
//    private val artifactStorage: ArtifactStorage
//) {
//
//    @PostMapping("/{pipelineId}/step-1")
//    fun runStep1(@PathVariable pipelineId: UUID): Map<String, String> {
//
//        val step0Yaml = artifactStorage.loadStep0Artifact(pipelineId)
//
//        service.generate(
//            pipelineId = pipelineId,
//            step0Yaml = step0Yaml
//        )
//
//        return mapOf(
//            "pipelineId" to pipelineId.toString(),
//            "status" to "WAITING_FOR_APPROVAL"
//        )
//    }
//}