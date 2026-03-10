package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineStepEntity

interface PipelineStepService {
    fun generate(pipeline: PipelineEntity, step: PipelineStepEntity)
    fun getStepType(): String
}
