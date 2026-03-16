package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "generation_logs")
@Access(AccessType.FIELD)
open class GenerationLogEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    open val pipeline: PipelineEntity,

    @Basic(optional = false)
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    open val message: String,

    @Column(name = "step_order")
    open val stepOrder: Int? = null,

    @Basic(optional = false)
    @Column(name = "created_at", nullable = false)
    open val createdAt: Instant = Instant.now()
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generation_logs_sequence")
    @SequenceGenerator(name = "generation_logs_sequence", sequenceName = "generation_logs_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? = id
}
