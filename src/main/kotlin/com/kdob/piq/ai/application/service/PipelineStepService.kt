package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.ArtifactStatus
import com.kdob.piq.ai.domain.model.StepType
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity

interface PipelineStepService {
    fun generate(pipelineStep: PipelineStepEntity)
    fun getStepType(): StepType
    fun getLabel(): String =
        getStepType().name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }

    fun updateArtifact(pipelineStep: PipelineStepEntity, yamlContent: String, status: ArtifactStatus)
}
