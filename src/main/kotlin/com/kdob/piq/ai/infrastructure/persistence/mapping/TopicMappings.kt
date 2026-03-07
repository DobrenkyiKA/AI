package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.Topic
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicEntity

fun Topic.toEntity(artifactStep0: ArtifactStep0Entity): TopicEntity =
    TopicEntity(
        key = key,
        title = title,
        description = description,
        artifactStep0 = artifactStep0,
        constraints = constraints.toEntity()
    )

fun TopicEntity.toDomain(): Topic =
    Topic(
        key = key,
        title = title,
        description = description,
        constraints = constraints.toDomain()
    )