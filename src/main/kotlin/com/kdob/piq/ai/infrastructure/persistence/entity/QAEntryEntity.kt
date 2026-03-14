package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "qa_entries")
@Access(AccessType.FIELD)
open class QAEntryEntity(
    @Basic(optional = false)
    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    open val questionText: String,

    @Basic(optional = false)
    @Column(nullable = false)
    open val level: String,

    @Basic(optional = true)
    @Column(columnDefinition = "TEXT")
    open var answer: String? = null,

    @Basic(optional = true)
    @Column(name = "short_answer", columnDefinition = "TEXT")
    open var shortAnswer: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_qa_id", nullable = false)
    open val topicQA: TopicQAEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "qa_entries_sequence")
    @SequenceGenerator(name = "qa_entries_sequence", sequenceName = "qa_entries_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}
