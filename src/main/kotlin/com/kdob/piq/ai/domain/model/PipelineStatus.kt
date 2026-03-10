package com.kdob.piq.ai.domain.model

enum class PipelineStatus {
    NEW,
    DRAFT,
    PENDING_FOR_ARTIFACT_APPROVAL,
    APPROVED,
    FAILED,
    QUESTIONS_PENDING_FOR_APPROVAL,
    QUESTIONS_APPROVED
}