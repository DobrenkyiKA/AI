package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.ArtifactStatus
import jakarta.persistence.*

@Entity
@Table(name = "questions_pipeline_artifacts")
@Access(AccessType.FIELD)
open class QuestionsPipelineArtifactEntity(
    pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "questionsArtifact", cascade = [CascadeType.ALL], orphanRemoval = true)
    open val topicsWithQuestions: MutableSet<PipelineTopicWithQuestionsEntity> = mutableSetOf()
) : PipelineArtifactEntity(pipeline)
