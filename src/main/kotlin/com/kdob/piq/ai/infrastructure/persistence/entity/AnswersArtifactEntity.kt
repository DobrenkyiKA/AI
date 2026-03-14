package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "answers_artifacts")
@Access(AccessType.FIELD)
open class AnswersArtifactEntity(
    pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "answersArtifact", cascade = [CascadeType.ALL], orphanRemoval = true)
    open val topicsWithQA: MutableSet<TopicQAEntity> = mutableSetOf()
) : PipelineArtifactEntity(pipeline)
