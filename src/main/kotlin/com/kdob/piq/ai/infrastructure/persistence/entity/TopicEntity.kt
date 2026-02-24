package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "topics")
@Access(AccessType.FIELD)
class TopicEntity(

    @Basic(optional = false)
    @Column(nullable = false, unique = true)
    private var key: String,

    @Basic(optional = false)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private var pipeline: PipelineEntity,

    @Basic(optional = false)
    private var title: String,

    @Basic(optional = false)
    @Column(columnDefinition = "TEXT")
    private var description: String,

    @Basic(optional = false)
    @Embedded
    private var constraints: ConstraintsEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "topics_sequence")
    @SequenceGenerator(name = "topics_sequence", sequenceName = "topics_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}