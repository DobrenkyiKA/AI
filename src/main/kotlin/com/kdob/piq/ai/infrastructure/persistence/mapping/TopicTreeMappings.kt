package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.TopicTreeNode
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicTreeNodeEntity

fun TopicTreeNode.toTopicTreeNodeEntity(topicTreeArtifact: TopicTreeArtifactEntity): TopicTreeNodeEntity =
    TopicTreeNodeEntity(
        key = key,
        name = name,
        parentTopicKey = parentTopicKey,
        coverageArea = coverageArea,
        depth = depth,
        leaf = leaf,
        topicTreeArtifact = topicTreeArtifact
    )

fun TopicTreeNodeEntity.toTopicTreeNode(): TopicTreeNode =
    TopicTreeNode(
        key = key,
        name = name,
        parentTopicKey = parentTopicKey,
        coverageArea = coverageArea,
        depth = depth,
        leaf = leaf
    )
