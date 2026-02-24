package com.kdob.piq.ai.infrastructure.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "content.pipeline.artifacts")
data class PipelineArtifactProperties(
    val rootDir: String
)