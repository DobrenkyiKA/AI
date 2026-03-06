package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.Topic
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicEntity

fun Topic.toEntity(pipeline: PipelineEntity): TopicEntity =
    TopicEntity(
        key = key,
        title = title,
        description = description,
        pipeline = pipeline,
        constraints = constraints.toEntity()
    )

fun TopicEntity.toDomain(): Topic =
    Topic(
        key = key,
        title = title,
        description = description,
        constraints = constraints.toDomain()
    )