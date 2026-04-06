package com.kdob.piq.ai.domain.model

enum class StepType(val artifactFileName: String) {
    TOPIC_TREE_GENERATION("topic-tree-artifact.yaml"),
    QUESTIONS_GENERATION("questions-artifact.yaml"),
    LONG_ANSWERS_GENERATION("answers-artifact.yaml"),
    SHORT_ANSWERS_GENERATION("short-answers-artifact.yaml");
}
