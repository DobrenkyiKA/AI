package com.kdob.piq.ai.domain.model

data class TopicDefinition(
    val pipeline: PipelineMeta,
    val topics: List<TopicMeta>,
    val constraints: Constraints,
    val generation: GenerationSettings,
    val exclusions: List<String>
)

data class PipelineMeta(
    val id: String,
    val createdBy: String,
    val createdAt: String
)

data class TopicMeta(
    val key: String,
    val title: String,
    val description: String
)

data class Constraints(
    val targetAudience: String,
    val experienceLevel: String,
    val intendedUsage: List<String>
)

data class GenerationSettings(
    val questionCount: Int,
    val avoidRedundancy: Boolean,
    val depth: String
)