package com.kdob.piq.ai.infrastructure.persistence.entity.artifact.answer

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Basic
import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "answers_artifacts")
@DiscriminatorValue("ANSWERS")
@Access(AccessType.FIELD)
open class AnswersArtifactEntity(
    pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "answersArtifact", cascade = [CascadeType.ALL], orphanRemoval = true)
    open val topicsWithQA: MutableSet<TopicQAEntity> = mutableSetOf()
) : PipelineArtifactEntity(pipeline)