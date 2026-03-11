package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.PipelineStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pipelines")
@Access(AccessType.FIELD)
open class PipelineEntity(
    @Basic(optional = false)
    @Column(name = "name", nullable = false, unique = true)
    open val name: String,

    @Basic(optional = false)
    @Column(name = "topic_key", nullable = false)
    open var topicKey: String,

    @OneToMany(mappedBy = "pipeline", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    open val steps: MutableList<PipelineStepEntity> = mutableListOf()
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipelines_sequence")
    @SequenceGenerator(name = "pipelines_sequence", sequenceName = "pipelines_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }

    @Basic(optional = false)
    @Column(name = "created_at", nullable = false)
    open val createdAt: Instant = Instant.now()

    @Basic(optional = false)
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    open var status: PipelineStatus = PipelineStatus.NEW
}