package com.kdob.piq.ai.domain.model

enum class PipelineStatus {
    NEW,
    GENERATION_IN_PROGRESS,
    WAITING_ARTIFACT_APPROVAL,
    ARTIFACT_APPROVED,
    FAILED
}
