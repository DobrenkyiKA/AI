package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.ArtifactStatus

data class PipelineArtifactUpdateRequest(
    val content: String,
    val status: ArtifactStatus
)
