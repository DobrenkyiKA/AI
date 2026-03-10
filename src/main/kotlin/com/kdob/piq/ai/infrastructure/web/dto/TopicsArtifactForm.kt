package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.PipelineTopic

data class TopicsArtifactForm(
    val topics: Set<PipelineTopic>
)