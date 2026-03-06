package com.kdob.piq.ai.infrastructure.web.dto

import com.fasterxml.jackson.annotation.JsonRootName
import com.kdob.piq.ai.domain.model.Topic

@JsonRootName("pipeline")
data class PipelineDefinitionForm(
    val name: String,
    val topics: Set<Topic>
)