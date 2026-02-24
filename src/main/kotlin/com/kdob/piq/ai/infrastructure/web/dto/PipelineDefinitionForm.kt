package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.Topic

data class PipelineDefinitionForm(
    val name: String,
    val topics: Set<Topic>
)