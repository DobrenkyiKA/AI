package com.kdob.piq.ai.domain.model

enum class PipelineStatus {
    DRAFT,
    PENDING_FOR_ARTIFACT_APPROVAL,
    APPROVED,
    FAILED,
    STEP_1_PENDING_FOR_APPROVAL,
    STEP_1_APPROVED
}