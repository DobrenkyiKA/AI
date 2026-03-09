package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "step_1_topics_with_questions")
@Access(AccessType.FIELD)
class Step1TopicWithQuestionsEntity(
    @Basic(optional = false)
    @Column(nullable = false)
    val key: String,

    @Basic(optional = false)
    val name: String,

    @ElementCollection
    @CollectionTable(name = "step_1_questions", joinColumns = [JoinColumn(name = "step_1_topic_id")])
    @Column(name = "question")
    val questions: MutableSet<String> = mutableSetOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifact_step_1_id", nullable = false)
    val artifactStep1: ArtifactStep1Entity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "step_1_topics_with_questions_sequence")
    @SequenceGenerator(name = "step_1_topics_with_questions_sequence", sequenceName = "step_1_topics_with_questions_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}
