package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "topics")
@Access(AccessType.FIELD)
class Step0TopicEntity(

    @Basic(optional = false)
    @Column(nullable = false)
    val key: String,

    @Basic(optional = false)
    val name: String,

    @Basic(optional = true)
    @Column(name = "parent_topic_key")
    val parentTopicKey: String?,

    @Basic(optional = false)
    @Column(name = "coverage_area", columnDefinition = "TEXT")
    val coverageArea: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "artifact_step_0_id", nullable = false)
    val artifactStep0: ArtifactStep0Entity,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "topics_sequence")
    @SequenceGenerator(name = "topics_sequence", sequenceName = "topics_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}