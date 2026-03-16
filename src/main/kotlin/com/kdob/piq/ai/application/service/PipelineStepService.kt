package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity

interface PipelineStepService {
    fun generate(step: PipelineStepEntity)
    fun getStepType(): String
    fun getLabel(): String =
        getStepType().split("_").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }

    fun updateArtifact(step: PipelineStepEntity, yamlContent: String, status: ArtifactStatus)
}
