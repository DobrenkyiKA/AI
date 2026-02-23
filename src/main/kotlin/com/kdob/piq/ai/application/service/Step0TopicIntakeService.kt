package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.Pipeline
import com.kdob.piq.ai.domain.PipelineStatus
import com.kdob.piq.ai.domain.TopicDefinition
import com.kdob.piq.ai.persistence.PipelineRepository
import com.kdob.piq.ai.storage.ArtifactStorage
import com.kdob.piq.ai.application.validation.TopicDefinitionValidator
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml
import java.time.Instant
import java.util.UUID


@Service
class Step0TopicIntakeService(
    private val repository: PipelineRepository,
    private val artifactStorage: ArtifactStorage
) {

    fun intake(yamlContent: String): Pipeline {

        val yaml = Yaml()
        val definition =
            yaml.loadAs(yamlContent, TopicDefinition::class.java)

        TopicDefinitionValidator.validate(definition)

        val pipeline = Pipeline(
            id = UUID.randomUUID(),
            name = definition.pipeline.id,
            status = PipelineStatus.WAITING_FOR_APPROVAL,
            createdAt = Instant.now()
        )

        repository.save(pipeline)

        artifactStorage.saveStep0Artifact(
            pipelineId = pipeline.id,
            yaml = yamlContent
        )

        return pipeline
    }
}