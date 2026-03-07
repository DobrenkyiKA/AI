package com.kdob.piq.ai.application.service.step0

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.mapping.toEntity
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val artifactStorage: ArtifactStorage
) {
    fun findAll() = pipelineRepository.findAll()
    fun findByName(name: String) = pipelineRepository.findByName(name)

    fun getArtifact(name: String, step: Int): String = artifactStorage.loadArtifact(name, step)

    fun getPipelineArtifact(name: String): String = getArtifact(name, 0)

    @Transactional
    fun updateArtifact(name: String, step: Int, yamlContent: String, status: ArtifactStatus): PipelineEntity {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")

        when (step) {
            0 -> {
                val updatedForm = yamlMapper.readValue(yamlContent, PipelineDefinitionForm::class.java)
                if (updatedForm.name != name) {
                    throw IllegalArgumentException("Pipeline name mismatch: expected $name but got ${updatedForm.name}")
                }
                PipelineValidator.validate(updatedForm)

                existing.artifactStep1 = null
                artifactStorage.deleteArtifact(name, 1)

                if (existing.artifactStep0 != null) {
                    existing.artifactStep0 = null
                    pipelineRepository.saveAndFlush(existing)
                }

                val artifactStep0 = ArtifactStep0Entity(pipeline = existing)
                artifactStep0.status = status
                artifactStep0.topics.addAll(updatedForm.topics.map { it.toEntity(artifactStep0) })
                existing.artifactStep0 = artifactStep0

                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.DRAFT
                }
                artifactStorage.saveArtifact(name, 0, yamlContent)
            }
            1 -> {
                val artifactStep1 = existing.artifactStep1 ?: throw IllegalStateException("Step 1 artifact not found")
                artifactStep1.status = status
                if (status == ArtifactStatus.APPROVED) {
                    existing.status = PipelineStatus.STEP_1_APPROVED
                } else if (status == ArtifactStatus.TO_BE_REGENERATED) {
                    existing.status = PipelineStatus.STEP_1_PENDING_FOR_APPROVAL
                }
                artifactStorage.saveArtifact(name, 1, yamlContent)
            }
            else -> throw IllegalArgumentException("Unsupported step: $step")
        }

        existing.updatedAt = Instant.now()
        return pipelineRepository.save(existing)
    }

    @Transactional
    fun updatePipeline(name: String, yamlContent: String): PipelineEntity {
        val existing = pipelineRepository.findByName(name)
            ?: throw NoSuchElementException("Pipeline not found: $name")
        return updateArtifact(name, 0, yamlContent, existing.artifactStep0?.status ?: ArtifactStatus.PENDING_FOR_APPROVAL)
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