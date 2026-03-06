package com.kdob.piq.ai.application.service.step0

import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm

object PipelineValidator {

    fun validate(pipeline: PipelineDefinitionForm) {

        require(pipeline.topics.isNotEmpty()) {
            "At least one topic must be defined"
        }

        pipeline.topics.forEach {
            require(it.key.isNotBlank()) {
                "Topic key must not be blank"
            }
        }

        pipeline.topics.forEach {
            require(it.constraints.questionCount in 1..100) {
                "Question count must be between 1 and 100"
            }
        }
    }
}