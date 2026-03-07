package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "topics")
@Access(AccessType.FIELD)
class TopicEntity(

    @Basic(optional = false)
    @Column(nullable = false, unique = true)
    val key: String,

    @Basic(optional = false)
    val title: String,

    @Basic(optional = false)
    @Column(columnDefinition = "TEXT")
    val description: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "artifact_step_0_id", nullable = false)
    val artifactStep0: ArtifactStep0Entity,

    @Basic(optional = false)
    @Embedded
    val constraints: ConstraintsEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "topics_sequence")
    @SequenceGenerator(name = "topics_sequence", sequenceName = "topics_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}