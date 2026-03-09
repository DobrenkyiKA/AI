package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.Step0ArtifactForm

fun PipelineEntity.toDomain(): Step0ArtifactForm =
    Step0ArtifactForm(
        topics = artifactStep0?.topics?.map { it.toDomain() }?.toSet() ?: emptySet()
    )
