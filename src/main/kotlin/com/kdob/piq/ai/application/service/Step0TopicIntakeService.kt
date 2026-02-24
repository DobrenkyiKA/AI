package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.Pipeline
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.model.TopicDefinition
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml
import java.time.Instant
import java.util.*


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