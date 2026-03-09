package com.kdob.piq.ai.infrastructure.client.question.dto

data class TopicClientResponse(
    val key: String,
    val name: String,
    val coverageArea: String = "",
    val exclusions: String = ""
)
