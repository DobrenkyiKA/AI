package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.ArtifactStatus
import jakarta.persistence.*

@Entity
@Table(name = "topics_pipeline_artifacts")
@Access(AccessType.FIELD)
open class TopicsPipelineArtifactEntity(
    pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "topicsArtifact", cascade = [CascadeType.ALL], orphanRemoval = true)
    open val topics: MutableSet<PipelineTopicEntity> = mutableSetOf()
) : PipelineArtifactEntity(pipeline)
