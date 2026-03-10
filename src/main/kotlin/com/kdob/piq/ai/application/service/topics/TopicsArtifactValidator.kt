package com.kdob.piq.ai.application.service.topics

import com.kdob.piq.ai.infrastructure.web.dto.TopicsArtifactForm

object TopicsArtifactValidator {

    fun validate(pipeline: TopicsArtifactForm) {

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