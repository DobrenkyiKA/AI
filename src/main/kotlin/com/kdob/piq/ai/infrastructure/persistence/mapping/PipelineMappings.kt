package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.Step0ArtifactForm

fun Step0ArtifactForm.toEntity(name: String): PipelineEntity {
    val pipeline = PipelineEntity(name = name)
    val artifactStep0 = ArtifactStep0Entity(pipeline = pipeline)
    artifactStep0.topics.addAll(topics.map { it.toEntity(artifactStep0) })
    pipeline.artifactStep0 = artifactStep0
    return pipeline
}

fun PipelineEntity.toDomain(): Step0ArtifactForm =
    Step0ArtifactForm(
        topics = artifactStep0?.topics?.map { it.toDomain() }?.toSet() ?: emptySet()
    )
