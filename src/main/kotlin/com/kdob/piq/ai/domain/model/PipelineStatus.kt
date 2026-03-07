package com.kdob.piq.ai.domain.model

enum class PipelineStatus {
    DRAFT,
    PENDING_FOR_ARTIFACT_APPROVAL,
    APPROVED,
    FAILED
}