package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.PipelineTopic
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineTopicEntity

fun PipelineTopic.toEntity(topicsArtifact: TopicsArtifactEntity): PipelineTopicEntity =
    PipelineTopicEntity(
        key = key,
        name = name,
        parentTopicKey = parentTopicKey,
        coverageArea = coverageArea,
        topicsArtifact = topicsArtifact,
    )

    fun PipelineTopicEntity.toDomain(): PipelineTopic =
        PipelineTopic(
            key = key,
            name = name,
            parentTopicKey = parentTopicKey,
            coverageArea = coverageArea,
        )