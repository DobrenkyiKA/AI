package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.TopicsArtifactForm

fun PipelineEntity.toDomain(): TopicsArtifactForm =
    TopicsArtifactForm(
        topics = topicsArtifact?.topics?.map { it.toDomain() }?.toSet() ?: emptySet()
    )
