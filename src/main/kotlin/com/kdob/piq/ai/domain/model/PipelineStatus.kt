package com.kdob.piq.ai.domain.model

enum class PipelineStatus {
    NEW,
    GENERATION_IN_PROGRESS,
    GENERATION_PAUSED,
    GENERATION_ABORTED,
    WAITING_ARTIFACT_APPROVAL,
    ARTIFACT_APPROVED,
    FAILED
}
