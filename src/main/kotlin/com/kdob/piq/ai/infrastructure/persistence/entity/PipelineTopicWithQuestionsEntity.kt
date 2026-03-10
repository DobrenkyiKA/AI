package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "pipeline_topic_questions")
@Access(AccessType.FIELD)
class PipelineTopicWithQuestionsEntity(
    @Basic(optional = false)
    @Column(nullable = false)
    val key: String,

    @Basic(optional = false)
    val name: String,

    @ElementCollection
    @CollectionTable(name = "pipeline_questions", joinColumns = [JoinColumn(name = "topic_question_id")])
    @Column(name = "question")
    val questions: MutableSet<String> = mutableSetOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questions_artifact_id", nullable = false)
    val questionsArtifact: QuestionsArtifactEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_topic_questions_sequence")
    @SequenceGenerator(name = "pipeline_topic_questions_sequence", sequenceName = "pipeline_topic_questions_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}
