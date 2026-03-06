package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm

fun PipelineDefinitionForm.toEntity(): PipelineEntity {
    val pipeline = PipelineEntity(name = name)
    pipeline.topics.addAll(topics.map { it.toEntity(pipeline) })
    return pipeline
}

fun PipelineEntity.toDomain(): PipelineDefinitionForm =
    PipelineDefinitionForm(
        name = name,
        topics = topics.map { it.toDomain() }.toSet()
    )
