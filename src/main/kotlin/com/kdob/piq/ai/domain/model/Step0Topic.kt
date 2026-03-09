package com.kdob.piq.ai.domain.model

data class Step0Topic(
    val key: String,
    val name: String,
    val parentTopicKey: String? = null,
    val coverageArea: String,
)