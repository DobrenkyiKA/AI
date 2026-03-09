package com.kdob.piq.ai.application.service.step0

import com.kdob.piq.ai.infrastructure.web.dto.Step0ArtifactForm

object Step0ArtifactValidator {

    fun validate(pipeline: Step0ArtifactForm) {

        require(pipeline.topics.isNotEmpty()) {
            "At least one topic must be defined"
        }

        pipeline.topics.forEach {
            require(it.key.isNotBlank()) {
                "Topic key must not be blank"
            }
        }
    }
}