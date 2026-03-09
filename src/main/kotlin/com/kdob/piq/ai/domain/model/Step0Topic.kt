package com.kdob.piq.ai.domain.model

data class Step0Topic(
    val key: String,
    val name: String,
    val parentTopicKey: String? = null,
    val coverageArea: String,
    val constraints: Constraints? = null,
)

data class Constraints(
    val targetAudience: String,
    val experienceLevel: String,
    val intendedUsage: List<String>,
    val exclusions: List<String>,
    val questionCount: Int
)