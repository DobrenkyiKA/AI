package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "topic_tree_nodes")
@Access(AccessType.FIELD)
open class TopicTreeNodeEntity(

    @Basic(optional = false)
    @Column(nullable = false)
    open var key: String,

    @Basic(optional = false)
    open var name: String,

    @Basic(optional = true)
    @Column(name = "parent_topic_key")
    open var parentTopicKey: String?,

    @Basic(optional = false)
    @Column(name = "coverage_area", columnDefinition = "TEXT")
    open var coverageArea: String,

    @Basic(optional = false)
    @Column(nullable = false)
    open var depth: Int = 0,

    @Basic(optional = false)
    @Column(nullable = false)
    open var leaf: Boolean = true,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "topic_tree_artifact_id", nullable = false)
    open val topicTreeArtifact: TopicTreeArtifactEntity,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "topic_tree_nodes_sequence")
    @SequenceGenerator(name = "topic_tree_nodes_sequence", sequenceName = "topic_tree_nodes_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}
