package com.kdob.piq.ai.application.service.step0

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import org.springframework.stereotype.Service

@Service
class Step0TopicIntakeService(
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage
) {

    private val yamlMapper = ObjectMapper(YAMLFactory())
        .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
        .registerKotlinModule()

    fun intake(yamlContent: String) {

        val pipeline = yamlMapper.readValue(yamlContent, PipelineDefinitionForm::class.java)

        PipelineValidator.validate(pipeline)

        pipelineRepository.save(pipeline)

        artifactStorage.saveStep0Artifact(
            pipelineName = pipeline.name,
            yaml = yamlContent
        )

    }
}