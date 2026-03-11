package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.PipelineTopic
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsPipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineTopicEntity

fun PipelineTopic.toPipelineTopicEntity(topicsArtifact: TopicsPipelineArtifactEntity): PipelineTopicEntity =
    PipelineTopicEntity(
        key = key,
        name = name,
        parentTopicKey = parentTopicKey,
        coverageArea = coverageArea,
        topicsArtifact = topicsArtifact
    )

fun PipelineTopicEntity.toPipelineTopic(): PipelineTopic =
    PipelineTopic(
        key = key,
        name = name,
        parentTopicKey = parentTopicKey,
        coverageArea = coverageArea
    )
