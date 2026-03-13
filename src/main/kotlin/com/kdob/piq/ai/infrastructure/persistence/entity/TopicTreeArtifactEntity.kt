package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "topic_tree_artifacts")
@Access(AccessType.FIELD)
open class TopicTreeArtifactEntity(
    pipeline: PipelineEntity,

    @Basic(optional = false)
    @Column(name = "max_depth", nullable = false)
    open val maxDepth: Int = 3,

    @Basic(optional = false)
    @OneToMany(mappedBy = "topicTreeArtifact", cascade = [CascadeType.ALL], orphanRemoval = true)
    open val nodes: MutableSet<TopicTreeNodeEntity> = mutableSetOf()
) : PipelineArtifactEntity(pipeline)
