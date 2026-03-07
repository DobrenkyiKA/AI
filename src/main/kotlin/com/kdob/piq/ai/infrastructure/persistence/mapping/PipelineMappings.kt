package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm

fun PipelineDefinitionForm.toEntity(): PipelineEntity {
    val pipeline = PipelineEntity(name = name)
    val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
    artifactStep0.topics.addAll(topics.map { it.toEntity(artifactStep0) })
    pipeline.artifactStep0 = artifactStep0
    return pipeline
}

fun PipelineEntity.toDomain(): PipelineDefinitionForm =
    PipelineDefinitionForm(
        name = name,
        topics = artifactStep0?.topics?.map { it.toDomain() }?.toSet() ?: emptySet()
    )
