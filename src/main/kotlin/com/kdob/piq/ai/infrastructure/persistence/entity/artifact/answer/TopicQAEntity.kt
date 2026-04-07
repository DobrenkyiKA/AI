package com.kdob.piq.ai.infrastructure.persistence.entity.artifact.answer

import com.kdob.piq.ai.infrastructure.persistence.entity.BaseEntity
import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Basic
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

@Entity
@Table(name = "topic_qa")
@Access(AccessType.FIELD)
open class TopicQAEntity(
    @Basic(optional = false)
    @Column(nullable = false)
    open val key: String,

    @Basic(optional = false)
    open val name: String,

    @Basic(optional = true)
    @Column(name = "parent_chain", length = 2048)
    open var parentChain: String? = null,

    @Basic(optional = false)
    @OneToMany(mappedBy = "topicQA", cascade = [CascadeType.ALL], orphanRemoval = true)
    open val entries: MutableList<QAEntryEntity> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answers_artifact_id", nullable = false)
    open val answersArtifact: AnswersArtifactEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "topic_qa_sequence")
    @SequenceGenerator(name = "topic_qa_sequence", sequenceName = "topic_qa_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}