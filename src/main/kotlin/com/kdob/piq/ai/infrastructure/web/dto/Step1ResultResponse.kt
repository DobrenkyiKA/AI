package com.kdob.piq.ai.infrastructure.web.dto

data class Step1ResultResponse(
    val pipelineId: String,
    val status: String,
    val questions: List<Map<String, String>>
)