package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.PipelineStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pipelines")
@Access(AccessType.FIELD)
class PipelineEntity(
    @Basic(optional = false)
    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Basic(optional = false)
    @OneToMany(mappedBy = "pipeline", cascade = [CascadeType.ALL], orphanRemoval = true)
    val topics: MutableSet<TopicEntity> = mutableSetOf()

) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipelines_sequence")
    @SequenceGenerator(name = "pipelines_sequence", sequenceName = "pipelines_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }

    @Basic(optional = false)
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()

    @Basic(optional = false)
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PipelineStatus = PipelineStatus.WAITING_FOR_APPROVAL
}