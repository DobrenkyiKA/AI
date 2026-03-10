package com.kdob.piq.ai.domain.model

data class PipelineTopic(
    val key: String,
    val name: String,
    val parentTopicKey: String? = null,
    val coverageArea: String,
)