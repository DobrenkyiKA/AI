package com.kdob.piq.ai.domain.model

data class TopicTreeNode(
    val key: String,
    val name: String,
    val parentTopicKey: String? = null,
    val coverageArea: String,
    val depth: Int = 0,
    val leaf: Boolean = true
)
