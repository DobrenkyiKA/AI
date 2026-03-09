package com.kdob.piq.ai.infrastructure.client.question.dto

data class CreateTopicClientRequest(
    val key: String,
    val name: String,
    val parentPath: String?,
    val coverageArea: String = "",
    val exclusions: String = ""
)
