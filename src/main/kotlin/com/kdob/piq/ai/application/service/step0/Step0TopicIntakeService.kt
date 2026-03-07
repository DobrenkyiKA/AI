package com.kdob.piq.ai.application.service.step0

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Step0TopicIntakeService(
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage
) {
    fun findAll() = pipelineRepository.findAll()
    fun findByName(name: String) = pipelineRepository.findByName(name)

    fun getPipelineArtifact(name: String): String = artifactStorage.loadStep0Artifact(name)

    @Transactional
    fun updatePipeline(name: String, yamlContent: String): PipelineEntity {
        val updatedForm = yamlMapper.readValue(yamlContent, PipelineDefinitionForm::class.java)
        if (updatedForm.name != name) {
            throw IllegalArgumentException("Pipeline name mismatch: expected $name but got ${updatedForm.name}")
        }

        PipelineValidator.validate(updatedForm)

        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

        // Update topics
        existing.topics.clear()
        existing.topics.addAll(updatedForm.topics.map { it.toEntity(existing) })

        existing.updatedAt = java.time.Instant.now()

        val saved = pipelineRepository.save(existing)

        artifactStorage.saveStep0Artifact(
            pipelineName = name,
            yaml = yamlContent
        )

        return saved
    }

    @Transactional
    fun deletePipeline(name: String) {
        pipelineRepository.deleteByName(name)
        artifactStorage.deleteArtifacts(name)
    }

    private val yamlMapper = ObjectMapper(YAMLFactory())
        .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
        .registerKotlinModule()

    fun intake(yamlContent: String): PipelineEntity {

        val pipeline = yamlMapper.readValue(yamlContent, PipelineDefinitionForm::class.java)

        PipelineValidator.validate(pipeline)

        val savedPipeline = pipelineRepository.save(pipeline)

        artifactStorage.saveStep0Artifact(
            pipelineName = savedPipeline.name,
            yaml = yamlContent
        )

        return pipelineRepository.findByName(savedPipeline.name)!!
    }
}