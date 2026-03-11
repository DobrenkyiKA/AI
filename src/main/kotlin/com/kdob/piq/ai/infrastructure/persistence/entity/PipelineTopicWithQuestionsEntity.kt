package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "pipeline_topic_questions")
@Access(AccessType.FIELD)
open class PipelineTopicWithQuestionsEntity(
    @Basic(optional = false)
    @Column(nullable = false)
    open val key: String,

    @Basic(optional = false)
    open val name: String,

    @ElementCollection
    @CollectionTable(name = "pipeline_questions", joinColumns = [JoinColumn(name = "topic_question_id")])
    @Column(name = "question")
    open val questions: MutableSet<String> = mutableSetOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questions_artifact_id", nullable = false)
    open val questionsArtifact: QuestionsPipelineArtifactEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_topic_questions_sequence")
    @SequenceGenerator(name = "pipeline_topic_questions_sequence", sequenceName = "pipeline_topic_questions_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}
