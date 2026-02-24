package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import org.springframework.stereotype.Service


@Service
class Step0TopicIntakeService(
    private val repository: PipelineRepository,
    private val artifactStorage: ArtifactStorage
) {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun intake(yamlContent: String) {

        val pipeline = yamlMapper.readValue(yamlContent, PipelineDefinitionForm::class.java)

        PipelineValidator.validate(pipeline)

        repository.save(pipeline)

        artifactStorage.saveStep0Artifact(
            pipelineName = pipeline.name,
            yaml = yamlContent
        )

    }
}