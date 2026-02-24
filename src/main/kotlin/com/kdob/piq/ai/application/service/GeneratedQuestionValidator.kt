package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.domain.model.GeneratedQuestion

object GeneratedQuestionValidator {

    fun validate(
        generated: List<GeneratedQuestion>,
        expectedCount: Int,
        existingPrompts: Set<String>,
        validTopicKeys: Set<String>
    ) {
        require(generated.size == expectedCount) {
            "Expected $expectedCount questions, got ${generated.size}"
        }

        val prompts = generated.map { it.prompt.trim().lowercase() }
        require(prompts.distinct().size == prompts.size) {
            "Duplicate questions generated"
        }

        require(prompts.none { it in existingPrompts }) {
            "Generated questions duplicate existing DB questions"
        }

        require(generated.all { it.topicKey in validTopicKeys }) {
            "Generated question contains invalid topic key"
        }
    }
}