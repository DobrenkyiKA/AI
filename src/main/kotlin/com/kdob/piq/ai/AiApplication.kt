package com.kdob.piq.ai

import com.kdob.piq.ai.infrastructure.storage.PipelineArtifactProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@EnableConfigurationProperties(PipelineArtifactProperties::class)
@SpringBootApplication
class AiApplication

fun main(args: Array<String>) {
    runApplication<AiApplication>(*args)
}
