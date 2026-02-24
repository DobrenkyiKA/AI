package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.TopicDefinition

object TopicDefinitionValidator {

    fun validate(definition: TopicDefinition) {

        require(definition.topics.isNotEmpty()) {
            "At least one topic must be defined"
        }

        definition.topics.forEach {
            require(it.key.isNotBlank()) {
                "Topic key must not be blank"
            }
        }

        require(definition.generation.questionCount in 1..100) {
            "Question count must be between 1 and 100"
        }
    }
}