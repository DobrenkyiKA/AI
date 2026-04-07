package com.kdob.piq.ai.infrastructure.persistence.entity.artifact.topic

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Basic
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "topic_tree_artifacts")
@DiscriminatorValue("TOPIC_TREE")
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